package com.radix.timberland.util

import scala.io.StdIn
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import ammonite.ops._
import ammonite.ops.ImplicitWd._
import cats.effect.{ContextShift, IO, Resource, SyncIO, Timer}
import cats.implicits._
import java.io.{File, FileInputStream, FileOutputStream, IOException}
import java.net.{InetAddress, NetworkInterface, ServerSocket}

object Util {
  def putStrLn(str: String): IO[Unit] = IO(scribe.info(str))

  def putStrLn(str: Vector[String]): IO[Unit] =
    if (str.isEmpty) {
      IO.pure(Unit)
    } else {
      putStrLn(str.reduce(_ + "\n" + _))
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
    import cats.effect.Timer
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
}
