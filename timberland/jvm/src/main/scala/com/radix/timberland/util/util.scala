package com.radix.timberland.util

import java.io.{File, FileInputStream, FileNotFoundException, FileOutputStream, IOException}
import java.net.{InetAddress, NetworkInterface, ServerSocket, UnknownHostException}

import ammonite.ops._
import cats.effect.{ContextShift, IO, Resource, Timer}
import cats.implicits._
import com.radix.utils.tls.ConsulVaultSSLContext.blaze
import org.http4s.Header
import org.http4s.Method._
import org.http4s.client.dsl.io._
import org.http4s.implicits._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.TimeoutException
import scala.concurrent.duration._

object Util {
  private[this] implicit val timer: Timer[IO] = IO.timer(global)
  private[this] implicit val cs: ContextShift[IO] = IO.contextShift(global)

  /** Let a specified function run for a specified period of time before interrupting it and raising an error. This
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

    val error = new TimeoutException(after.toString)
    timeoutTo(fa, after, IO.raiseError(error))
  }

  /** Creates a race condition between two functions (fa and timer.sleep()) that will let a program run until the timer
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
  def timeoutTo[A](fa: IO[A], after: FiniteDuration, fallback: IO[A])(
    implicit timer: Timer[IO],
    cs: ContextShift[IO]
  ): IO[A] = {

    IO.race(fa, timer.sleep(after)).flatMap {
      case Left(a)  => IO.pure(a)
      case Right(_) => fallback
    }
  }

  /** Wait for a DNS record to become available. Consul will not return a record for failing services.
   * This returns Unit because we do not care what the result is, only that there is at least one.
   *
   * @param dnsName         The DNS name to look up
   * @param timeoutDuration How long to wait before throwing an exception
   */
  def waitForDNS(dnsName: String, timeoutDuration: FiniteDuration): IO[Unit] = {
    scribe.info(s"waiting for $dnsName to resolve, a maximum of $timeoutDuration")
    def queryLoop(): IO[Unit] =
      for {
        lookupResult <- IO(InetAddress.getAllByName(dnsName)).attempt
        _ <- lookupResult match {
          case Left(_: UnknownHostException) => IO.sleep(2.seconds) *> queryLoop
          case Left(err: Throwable)          => LogTUI.dns(ErrorDNS(dnsName)) *> IO.raiseError(err)
          case Right(addresses: Array[InetAddress]) =>
            addresses match {
              case Array() => LogTUI.dns(EmptyDNS(dnsName)) *> IO.sleep(2.seconds) *> queryLoop
              case _       => LogTUI.dns(ResolveDNS(dnsName))
            }
        }
      } yield ()

    LogTUI.dns(WaitForDNS(dnsName)) *> timeout(queryLoop, timeoutDuration) *> IO.unit
  }

  /**
   *
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
    scribe.info(s"waiting for $port be up, a maximum of $timeoutDuration")
    def queryProg(): IO[Boolean] =
      for {
        portUp <- isPortUp(port)
        _ <- if (!portUp) {
          IO.sleep(1.second) *> queryProg
        } else IO(portUp)
      } yield portUp

    timeout(queryProg, timeoutDuration)
  }

  def waitForConsul(
    consulToken: String,
    timeoutDuration: FiniteDuration,
    address: String = "https://consul.service.consul:8501"
  ): IO[Unit] = {
    import com.radix.utils.tls.TrustEveryoneSSLContext.insecureBlaze
    scribe.info(s"waiting for Consul (@ 8501) to be leader, a max of $timeoutDuration.")
    val pollUrl = s"$address/v1/status/leader"
    import org.http4s.Uri
    val request = GET(
      Uri.fromString(pollUrl).getOrElse(uri"https://127.0.0.1:8501/v1/status/leader"),
      Header("X-Consul-Token", consulToken)
    )
    // there's a few hundred milliseconds between when consul has a leader and when the single-leader node syncs ACLs... add a silly 2 second sleep after leader to soak up this tiny race
    def queryConsul: IO[Unit] = {
      insecureBlaze
        .use(_.expect[String](request))
        .flatMap({
          case "\"\""    => IO.sleep(1 second) *> queryConsul
          case _ @leader => IO(scribe.info(s"Consul has leader: $leader")) *> IO.sleep(2.second) *> IO.unit
        })

    }
    timeout(queryConsul, timeoutDuration)
  }

  def waitForNomad(timeoutDuration: FiniteDuration): IO[Unit] = {
    scribe.info(s"waiting for Nomad (@ 4647) to be leader, a max of $timeoutDuration.")
    val request = GET(uri"https://nomad.service.consul:4646/v1/status/leader")

    def queryNomad: IO[Unit] = {
      blaze
        .use(_.expect[String](request))
        .flatMap({
          case "\"\""    => IO.sleep(1 second) *> queryNomad
          case _ @leader => IO(scribe.info(s"Nomad has leader: $leader")) *> IO.unit
        })

    }
    timeout(queryNomad, timeoutDuration)
  }

  def waitForSystemdString(serviceName: String, stringToFind: String, timeoutDuration: FiniteDuration): IO[Unit] = {
    def queryLoop(): IO[Boolean] =
      for {
        timestampOutput <- exec(s"systemctl show -p ActiveEnterTimestamp $serviceName").map(_.stdout)
        timestamp = timestampOutput.split(" ").slice(1, 3).mkString(" ")
        journalctlLog <- execArr(List("journalctl", "-e", "-u", serviceName, "--since", timestamp))
        lookupResult <- IO(journalctlLog.stdout.contains(stringToFind))
        _ <- lookupResult match {
          case false => IO.sleep(2.seconds) *> queryLoop
          case true  => IO.pure(true)
        }
      } yield lookupResult
    timeout(queryLoop, timeoutDuration) *> IO.unit
  }

  def waitForPathToExist(checkPath: os.Path, timeoutDuration: FiniteDuration): IO[Unit] = {
    scribe.info(s"waiting for path $checkPath to exist, a max of $timeoutDuration.")
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

  // needed because CommandResult can't be redirected and read, or even read multiple times...
  case class ProcOut(exitCode: Int, stdout: String, stderr: String)

  def exec(command: String, cwd: Path = os.root, env: Map[String, String] = Map.empty): IO[ProcOut] =
    execArr(command.split(" "), cwd, env)

  def execArr(command: Seq[String], cwd: Path = os.root, env: Map[String, String] = Map.empty): IO[ProcOut] = IO {
    scribe.info(s"Calling: $command")
    val res = os
      .proc(command)
      .call(cwd = cwd, env = env, check = false, stdout = os.Pipe, stderr = os.Pipe)
    val stdout: String = res.out.text
    val stderr: String = res.err.text
    val output = ProcOut(res.exitCode, stdout, stderr)
    scribe.info(
      s" got result code: ${res.exitCode}, stderr: $stderr (${stderr.length}), stdout: $stdout (${stdout.length})"
    )
    output
  }

  /***
   * Log (using scribe) the arguments to an os.proc call.
   * @param cmd, a list of os.Shellable
   * @return result of os.proc call, ready to be call()'d or spawn()'d
   */
  def proc(cmd: os.Shellable*): os.proc = {
    val cmdString = s"${cmd.value.reduce((a, b) => s"$a $b")}"
    scribe.info(s"Calling: $cmdString")
    os.proc(cmd)
  }

  //This is lazy since this environment variable only should exist when
  lazy val monorepoDir: Path = sys.env.get("RADIX_MONOREPO_DIR").map(Path(_)).getOrElse(os.pwd)

  def nioCopyFile(from: File, to: File): IO[File] =
    for {
      _ <- IO {
        scribe.trace(s"copying ${from.toPath.toAbsolutePath.toString} to ${to.toPath.toAbsolutePath.toString}")
      }
      fis <- IO { new FileInputStream(from) }
      fos <- IO { new FileOutputStream(to) }
      xfered <- IO { fos.getChannel.transferFrom(fis.getChannel, 0, Long.MaxValue) }
      _ <- IO { fos.flush() }
      _ <- IO { fos.close() }
      _ <- IO { fis.close() }
      _ <- IO {
        scribe.trace(s"xfered $xfered ${from.toPath.toAbsolutePath.toString} to ${to.toPath.toAbsolutePath.toString}")
      }
      done <- IO { to }
    } yield done

  def getDefaultGateway: String = {
    val sock = new java.net.DatagramSocket()
    sock.connect(InetAddress.getByName("8.8.8.8"), 10002)
    sock.getLocalAddress.getHostAddress
  }
  def getIfFromIP(ip: String): String = {
    NetworkInterface
      .getByInetAddress(InetAddress.getByAddress("localhost", ip.split('.').map(_.toInt.toByte)))
      .getName
  }
  def getNetworkInterfaces: IO[List[String]] = IO {
    NetworkInterface.getNetworkInterfaces.asScala.toList
      .flatMap(_.getInetAddresses.asScala)
      .filter(_.getAddress.length == 4) // only ipv4
      .map(_.getHostAddress)
      .distinct
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
  private val minioFolders = List(
    persistentDir / "nginx",
    persistentDir / "minio_data",
    persistentDir / "minio_data" / "userdata"
  )
  private val otherFolders = List(
    runtime / "terraform",
    runtime / "zookeeper_data",
    runtime / "kafka_data",
    runtime / "ybmaster_data",
    runtime / "ybtserver_data"
  )
  (minioFolders ++ otherFolders).map(path => os.makeDir.all(path))

}
