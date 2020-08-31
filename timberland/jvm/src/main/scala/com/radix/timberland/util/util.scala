package com.radix.timberland.util

import java.io.{File, FileInputStream, FileOutputStream, IOException}
import java.net.{InetAddress, NetworkInterface, ServerSocket, UnknownHostException}

import ammonite.ops._
import cats.effect.{ContextShift, IO, Resource, Timer}
import cats.implicits._
import com.radix.utils.tls.ConsulVaultSSLContext.blaze
import org.http4s.Header
import org.http4s.Method._
import org.http4s.client.dsl.io._
import org.http4s.implicits._
import oshi.SystemInfo

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

  def waitForConsul(consulToken: String, timeoutDuration: FiniteDuration): IO[Unit] = {
    scribe.info(s"waiting for Consul (@ 8501) to be leader, a max of $timeoutDuration.")
    val request = GET(uri"https://127.0.0.1:8501/v1/status/leader", Header("X-Consul-Token", consulToken))
    // there's a few hundred milliseconds between when consul has a leader and when the single-leader node syncs ACLs... add a silly 2 second sleep after leader to soak up this tiny race
    def queryConsul: IO[Unit] = {
      blaze
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
        journalctlLog <- exec(s"sudo journalctl -xe -u $serviceName")
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

  def exec(command: String, cwd: Path = os.root): IO[ProcOut] = IO {
    scribe.info(s"Calling: $command")
    val res = os
      .proc(command.split(' ')) // dodgy assumption, but fine for most uses
      .call(cwd = cwd, check = false, stdout = os.Pipe, stderr = os.Pipe)
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

  object RootShell {
    private implicit val timer = IO.timer(global)
    private var sudopw: Option[String] = None
    private def checkSudo: String = {
      if (sudopw.isEmpty) {
        import sys.process._
        Seq("/bin/sh", "-c", "stty -echo < /dev/tty").!
        try { // there's not much that can go wrong here but we REALLY don't want to leave echo
          // off on the user's shell if something does break
          sudopw = Some(readLine("please enter your sudo password: "))
          println
        } finally {
          Seq("/bin/sh", "-c", "stty echo < /dev/tty").!
        }
      }
      sudopw.get // TODO(lily) this should never fail; can I guarantee that statically?
    }
    final case class RootShell(proc: os.SubProcess) {
      def read(file: os.Path): IO[String] = IO(os.read(file))
      def overwrite(in: String, file: os.Path): IO[Unit] = exec(s"/bin/echo '$in' > '$file'") *> exec("/bin/sync")
      def append(in: String, file: os.Path): IO[Unit] = exec(s"/bin/echo '$in' >> '$file'") *> exec("/bin/sync")
      def move(from: os.Path, to: os.Path): IO[Unit] = exec(s"/bin/mv $from $to") *> exec("/bin/sync")
      def remove(path: os.Path): IO[Unit] = exec(s"/bin/rm $path") *> exec("/bin/sync")
      def copy(from: os.Path, to: os.Path): IO[Unit] = exec(s"/bin/cp $from $to") *> exec("/bin/sync")
      def exec(in: String): IO[Unit] =
        IO {
          scribe.debug(s"root shell is executing $in")
          proc.stdin.writeLine(in)
        } *> IO.sleep(1.second)
    }
    private def aquire: IO[RootShell] =
      IO({
        checkSudo
        try {
          os.proc(Seq("/usr/bin/sudo", "-Sp", "", "su")).call(stdin = s"${sudopw.get}\n")
        } catch {
          case exn: os.SubprocessException => ()
        }
        RootShell(os.proc(Seq("/usr/bin/sudo", "-Sp", "", "su")).spawn())
      })
    private def release: RootShell => IO[Unit] = { rootshell =>
      IO {
        val sp = rootshell.proc
        sp.stdin.close()
        sp.close()
        sp.destroy()
      }
    }
    val resource: Resource[IO, RootShell] = Resource.make(aquire)(release)
  }

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
}

object RadPath {
  val osname = new SystemInfo().getOperatingSystem().getFamily()

  def runtime: os.Path = {
    osname match {
      case "Windows" => os.root / "Program Files" / "Radix" // "os.root" evaluates to "C:\" on windows machines
      case _         => os.root / "opt" / "radix" // This should work on all non-windows machines, right?
    }
  }

  def temp: os.Path = {
    osname match {
      case "Windows" => os.home / "AppData" / "Local" / "Temp" / "Radix" // When in Rome, etc.
      case _         => os.root / "tmp" / "radix"
    }
  }
}
