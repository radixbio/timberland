package com.radix.timberland.util

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import ammonite.ops._
import ammonite.ops.ImplicitWd._
import cats.effect.{IO, SyncIO}
import cats.implicits._
import cats.effect.ContextShift
import java.io.{File, FileInputStream, FileOutputStream}

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
      fis    <- IO { new FileInputStream(from) }
      fos    <- IO { new FileOutputStream(to) }
      xfered <- IO { fos.getChannel.transferFrom(fis.getChannel, 0, Long.MaxValue) }
      _      <- IO { fos.flush() }
      _      <- IO { fos.close() }
      _      <- IO { fis.close() }
      _ <- IO {
        scribe.trace(s"xfered $xfered ${from.toPath.toAbsolutePath.toString} to ${to.toPath.toAbsolutePath.toString}")
      }
      done <- IO { to }
    } yield done
}
