package com.radix.timberland.util

import java.io.{FileNotFoundException, IOException}
import java.net.{InetAddress, ServerSocket, UnknownHostException}
import ammonite.ops._
import cats.effect.{ConcurrentEffect, ContextShift, IO, Sync, Timer}
import com.radix.utils.tls.ConsulVaultSSLContext.blaze
import org.http4s.{Header, Uri}
import org.http4s.Method._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.dsl.io._
import org.http4s.implicits._
import org.log4s.LogLevel
import os.ProcessOutput
import scribe.Level

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.TimeoutException
import scala.concurrent.duration._

object Util {
  private[this] implicit val timer: Timer[IO] = IO.timer(global)
  private[this] implicit val cs: ContextShift[IO] = IO.contextShift(global)

  val isLinux: Boolean = System.getProperty("os.name").toLowerCase.contains("linux")

  /**
   * Let a specified function run for a specified period of time before interrupting it and raising an error. This
   * function sets up the timeoutTo function.
   *
   * Taken from: https://typelevel.org/cats-effect/datatypes/io.html#race-conditions--race--racepair
   *
   * @param fa    The function to run (This function must return type IO[A])
   * @param after Timeout after this amount of time
   * @param timer A default Timer
   * @param cs    A default ContextShift
   * @tparam A The return type of the function must be IO[A]. A is the type of our result
   * @return Returns the successful completion of the function or a IO.raiseError
   */
  def timeout[A](fa: IO[A], after: FiniteDuration)(implicit timer: Timer[IO], cs: ContextShift[IO]): IO[A] = {

    val error =
      new TimeoutException(
        after.toString
      ) // this should give more info about what is being run, maybe a message argument?
    timeoutTo(fa, after, IO.raiseError(error))
  }

  /**
   * Creates a race condition between two functions (fa and timer.sleep()) that will let a program run until the timer
   * expires
   *
   * Taken from: https://typelevel.org/cats-effect/datatypes/io.html#race-conditions--race--racepair
   *
   * @param fa       The function to race which must return type IO[A]
   * @param after    The duration to let the function run
   * @param fallback The function to run if fa fails
   * @param timer    A default timer
   * @param cs       A default ContextShift
   * @tparam A The type of our result
   * @return Returns the result of fa if it completes within @after or returns fallback (all IO[A])
   */
  def timeoutTo[A](fa: IO[A], after: FiniteDuration, fallback: IO[A])(implicit
    timer: Timer[IO],
    cs: ContextShift[IO],
  ): IO[A] = {

    IO.race(fa, timer.sleep(after)).flatMap {
      case Left(a)  => IO.pure(a)
      case Right(_) => fallback
    }
  }

  /**
   * @param port The port number to check (on localhost)
   * @return Whether the port is up
   */
  def isPortUp(port: Int): IO[Boolean] = IO {
    val socket =
      try {
        val serverSocket = new ServerSocket(port)
        serverSocket.setReuseAddress(true)
        Some(serverSocket)
      } catch {
        case _: IOException => None
      }
    socket.map(_.close()).isEmpty
  }

  def waitForPortUp(port: Int, timeoutDuration: FiniteDuration): IO[Boolean] = {
    scribe.debug(s"waiting for $port be up, a maximum of $timeoutDuration")
    def queryProg(): IO[Boolean] =
      for {
        portUp <- isPortUp(port)
        _ <-
          if (!portUp) {
            IO.sleep(1.second) *> queryProg
          } else IO(portUp)
      } yield portUp

    timeout(queryProg, timeoutDuration)
  }

  def waitForConsul(
    consulToken: String,
    timeoutDuration: FiniteDuration,
    address: String = "https://consul.service.consul:8501",
  ): IO[Unit] = {
    import com.radix.utils.tls.TrustEveryoneSSLContext.insecureBlaze
    scribe.debug(s"waiting for Consul (@ 8501) to be leader, a max of $timeoutDuration.")
    val pollUrl = s"$address/v1/status/leader"
    import org.http4s.Uri
    val requestIO = GET(
      Uri.fromString(pollUrl).getOrElse(uri"https://127.0.0.1:8501/v1/status/leader"),
      Header("X-Consul-Token", consulToken),
    )
    // there's a few hundred milliseconds between when consul has a leader and when the single-leader node syncs ACLs... add a silly 2 second sleep after leader to soak up this tiny race
    def queryConsul: IO[Unit] = requestIO.map { request =>
      insecureBlaze
        .use(_.expectOption[String](request))
        .flatMap({
          case Some("\"\"") | None => IO.sleep(1 second) *> queryConsul
          case Some(_ @leader)     => IO(scribe.debug(s"Consul has leader: $leader")) *> IO.sleep(2.second) *> IO.unit
        })

    }
    timeout(queryConsul, timeoutDuration)
  }

  /**
   * This function should only be used during the bootstrap process since it doesn't take ServiceAddrs into account
   */
  def waitForNomad(timeoutDuration: FiniteDuration): IO[Unit] = {
    scribe.debug(s"waiting for Nomad (@ 4647) to be leader, a max of $timeoutDuration.")
    val requestIO = GET(uri"https://127.0.0.1:4646/v1/status/leader")

    def queryNomad: IO[Unit] = requestIO.map { request =>
      blaze
        .use(_.expectOption[String](request))
        .flatMap({
          case Some("\"\"") | None => IO.sleep(1 second) *> queryNomad
          case Some(_ @leader)     => IO(scribe.debug(s"Nomad has leader: $leader")) *> IO.unit
        })
    }
    timeout(queryNomad, timeoutDuration)
  }

  def waitForSystemdString(serviceName: String, stringToFind: String, timeoutDuration: FiniteDuration): IO[Unit] = {
    def queryLoop: IO[Boolean] =
      for {
        timestampOutput <- exec(s"systemctl show -p ActiveEnterTimestamp $serviceName").map(_.stdout)
        timestamp = timestampOutput.split(' ').slice(1, 3).mkString(" ")
        journalctlLog <-
          if (timestamp.nonEmpty) {
            execArr(List("journalctl", "-e", "-u", serviceName, "--since", timestamp))
          } else {
            execArr(List("journalctl", "-u", serviceName))
          }
        lookupResult <- IO(journalctlLog.stdout.contains(stringToFind))
        _ <- lookupResult match {
          case false => IO.sleep(2.seconds) *> queryLoop
          case true  => IO.pure(true)
        }
      } yield lookupResult

    if (isLinux) timeout(queryLoop, timeoutDuration) *> IO.unit else IO.sleep(timeoutDuration)
  }

  def waitForPathToExist(checkPath: os.Path, timeoutDuration: FiniteDuration): IO[Unit] = {
    scribe.debug(s"waiting for path $checkPath to exist, a max of $timeoutDuration.")
    def queryLoop(): IO[Boolean] =
      for {
        exists <- IO(os.exists(checkPath))
        checkResult <- exists match {
          case false => IO.sleep(2.seconds) *> queryLoop
          case true  => IO.pure(true)
        }
      } yield checkResult
    timeout(queryLoop, timeoutDuration) *> IO.unit
  }

  def waitForServiceDNS(service: String, timeoutDuration: FiniteDuration): IO[Boolean] = {
    def retry: IO[Boolean] = IO.sleep(2.seconds) *> waitForServiceDNS(service, timeoutDuration - 2.seconds)

    for {
      lookupResult <- IO {
        InetAddress.getAllByName(s"$service.service.consul")
      }.attempt
      success <- lookupResult match {
        case Left(_: UnknownHostException) => retry
        case Left(_: Throwable)            => retry
        case Right(_: Array[InetAddress])  => IO.pure(true)
      }
    } yield success
  }

  // needed because CommandResult can't be redirected and read, or even read multiple times...
  case class ProcOut(exitCode: Int, stdout: String, stderr: String)

  def scribePipe(level: Level = Level.Trace): ProcessOutput.ReadBytes =
    os.ProcessOutput((stream: Array[Byte], len: Int) =>
      scribe.log(level, new String(stream.take(len), StandardCharsets.UTF_8), None)
    )

  def exec(command: String, cwd: os.Path = os.root, env: Map[String, String] = Map.empty): IO[ProcOut] =
    execArr(command.split(" "), cwd, env)

  def execArr(
    command: Seq[String],
    cwd: os.Path = os.root,
    env: Map[String, String] = Map.empty,
    spawn: Boolean = false,
  ): IO[ProcOut] = IO {
    scribe.debug(s"Calling: $command")
    val cmd = os.proc(command)
    if (spawn) {
      cmd.spawn(cwd = cwd, env = env, stdout = scribePipe(), stderr = scribePipe())
      ProcOut(0, "", "")
    } else {
      val res = cmd.call(cwd = cwd, env = env, check = false, stdout = os.Pipe, stderr = os.Pipe)
      val stdout: String = res.out.text
      val stderr: String = res.err.text
      val output = ProcOut(res.exitCode, stdout, stderr)
      scribe.log(
        if (res.exitCode == 0) Level.Trace else Level.Debug,
        s" got result code: ${res.exitCode}, stderr: $stderr (${stderr.length}), stdout: $stdout (${stdout.length})",
        None,
      )
      output
    }
  }

  /**
   * *
   * Log (using scribe) the arguments to an os.proc call.
   * @param cmd, a list of os.Shellable
   * @return result of os.proc call, ready to be call()'d or spawn()'d
   */
  def proc(cmd: os.Shellable*): os.proc = {
    val cmdString = s"${cmd.value.reduce((a, b) => s"$a $b")}"
    scribe.debug(s"Calling: $cmdString")
    os.proc(cmd)
  }

  def addWindowsFirewallRules(): IO[Unit] = {
    def addFirewallRule(name: String, action: String = "allow", protocol: String = "TCP", port: Int): IO[Unit] = {
      val inCmdStr =
        s"netsh advfirewall firewall add rule name=$name-In dir=in action=$action protocol=$protocol localport=$port"
      val outCmdStr =
        s"netsh advfirewall firewall add rule name=$name-Out dir=out action=$action protocol=$protocol localport=$port"
      Util.exec(inCmdStr) *> Util.exec(outCmdStr) *> IO.unit
    }

    for {
      _ <- addFirewallRule("Consul_HTTP", port = 8500)
      _ <- addFirewallRule("Consul_HTTPS", port = 8501)
      _ <- addFirewallRule("Consul_GRPC", port = 8502)
      _ <- addFirewallRule("Consul_RPC", port = 8300)
      _ <- addFirewallRule("Consul_LAN_Serf_TCP", port = 8301)
      _ <- addFirewallRule("Consul_LAN_Serf_UDP", protocol = "UDP", port = 8301)
      _ <- addFirewallRule("Vault_HTTP", port = 8200)
      _ <- addFirewallRule("Nomad_HTTP", port = 4646)
      _ <- addFirewallRule("Nomad_RCP", port = 4747)
    } yield ()
  }

  def hashFile[F[_]: Sync](file: Path): F[String] = {
    Sync[F].delay {
      val digester = MessageDigest.getInstance("SHA-256")
      val hash = digester.digest(Files.readAllBytes(file))
      hash.map(b => String.format("%02X", b)).mkString.toLowerCase // convert to hex
    }
  }

  def getPublicIp: IO[Option[String]] = {
    BlazeClientBuilder[IO](global).resource.use { client =>
      GET(Uri.unsafeFromString("http://checkip.amazonaws.com"))
        .flatMap(client.expectOption[String](_))
        .map(_.map(_.stripLineEnd))
    }
  }
}

object RadPath {

  val osname = System.getProperty("os.name") match {
    case mac if mac.toLowerCase.contains("mac")             => "darwin"
    case linux if linux.toLowerCase.contains("linux")       => "linux"
    case windows if windows.toLowerCase.contains("windows") => "windows"
  }
  // "os.root" evaluates to "C:\" on windows machines, "/" on *nix
  // we use C:\opt\radix\ for installing timberland at this point.
  // this can be a match statement again if necessary
  def runtime: os.Path = os.root / "opt" / "radix"

  def temp: os.Path = {
    osname match {
      case "windows" => os.home / "AppData" / "Local" / "Temp" / "Radix" // When in Rome, etc.
      case _         => os.root / "tmp" / "radix"
    }
  }
  val fileExtension = osname match {
    case "windows" => ".exe"
    case _         => ""
  }

  val cni: os.Path = os.root / "opt" / "cni" / "bin"
  val persistentDir: os.Path = runtime / "timberland"
  val nomadExec: os.Path = persistentDir / "nomad" / s"nomad$fileExtension"
  val consulExec: os.Path = persistentDir / "consul" / s"consul$fileExtension"
  val consulTemplateExec: os.Path = persistentDir / "consul-template" / s"consul-template$fileExtension"
  if (!os.exists(consulExec)) {
    throw new FileNotFoundException(s"Could not find ${consulExec.toString()}")
  }
  if (!os.exists(nomadExec)) {
    throw new FileNotFoundException(s"Could not find ${nomadExec.toString()}")
  }
  List(
    runtime / "terraform",
    runtime / "zookeeper_data",
    runtime / "kafka_data",
    runtime / "ybmaster_data",
    runtime / "ybtserver_data",
    runtime / "elasticsearch_data",
    runtime / "ipfs_data",
    runtime / "ipfs_shared",
    runtime / "config",
    runtime / "config" / "modules",
  ).foreach(os.makeDir.all)
  if (Util.isLinux) os.perms.set(runtime / "ipfs_data", os.PermSet.fromInt(775))

}
