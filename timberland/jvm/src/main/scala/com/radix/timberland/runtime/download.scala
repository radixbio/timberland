package com.radix.timberland.runtime

import ammonite._
import ammonite.ops._
import cats._
import cats.data._
import cats.effect.{ContextShift, Effect, IO}
import cats.implicits._
import java.net.URL
import java.io.{BufferedInputStream, BufferedReader, File, FileOutputStream, InputStreamReader}

import scala.concurrent.ExecutionContext
import scala.collection.JavaConverters._
import java.nio.channels.Channels
import java.util.concurrent.Executors
import java.util.zip.ZipFile

import com.radix.timberland.radixdefs._

import scala.language.higherKinds

object Download {
  implicit val ec: ExecutionContext           = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(8))
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)
  val bcs: ContextShift[IO]                   = IO.contextShift(ec)

  /**
    * A simple implementation that works for the way Hashicorp's repos are set up
    * @param F the effect instance
    * @param E the applicative instane
    * @tparam F The effect type
    */
  abstract class SimpleSHAVTable[F[_]](implicit F: Effect[F], E: Applicative[IO]) extends SHAVTableDownloader[F] {
    val t: T
    override def get(downloadURL: URL, shavtable: URL, predicates: List[String]): F[(File, T)] = F.liftIO {
      val res =
        for {
          _ <- IO {
            scribe.info(
              s"starting SHAVTable aquisition from $downloadURL with shavtable url $shavtable with predicates $predicates")
          }
          shatosplice <- IO {
            new BufferedReader(new InputStreamReader(shavtable.openStream()))
              .lines()
              .filter(shaline => predicates.map(shaline.contains).reduce(_ && _))
              .iterator()
              .asScala
              .toList
              .sortBy(_.length)
          }
          _ <- IO(scribe.info(s"got shas to splices $shatosplice"))
          _ <- if (shatosplice.isEmpty) {
            IO.raiseError(
              new IllegalStateException(
                s"couldn't find any shas that matched all of $predicates from url ${shavtable.toString}"))
          } else { IO.unit }
          _ <- if (shatosplice.length > 1) {
            IO(scribe.warn(
              s"there's more than one sha that matches preconditions $predicates : $shatosplice. Choosing the first one... ${shatosplice.head.split("  ").toList}"))
          } else { IO.unit }
          todl <- IO.pure(shatosplice.head.split("  ")(1) match {
            case san if san.startsWith("./") => san.drop(2)
            case els                         => els
          })

          route <- IO {
            new URL(downloadURL.toString + todl)
          }
          file <- IO {
            val tmp = File.createTempFile(todl, ".radix")
            scribe.info(s"created ${tmp.toPath.toAbsolutePath.toString}")
            val fout =
              new FileOutputStream(tmp)
            val xfered = fout.getChannel.transferFrom(Channels.newChannel(route.openStream()), 0, Long.MaxValue)
            fout.flush()
            fout.close()
            scribe.trace(s"""file $tmp has been transferred $xfered and has file size ${tmp.length()}.
                 |it's sha is supposed to be ${shatosplice.head}
               """.stripMargin)
            scribe.info(s"got contents of $route transferred to ${tmp.toPath.toAbsolutePath.toString}")
            (tmp, t)

          }
          _ <- IO(scribe.info(s"completed shavtable for url $downloadURL, $shavtable, predicates: $predicates"))

        } yield file

      IO.shift(bcs) *> res <* IO.shift
    }
  }

  /**
    * A SHAVTable downloader that downloads zip files
    * @param F the effect instance
    * @tparam F The effect type
    */
  abstract class ZipSHADL[F[_]](implicit F: Effect[F]) extends SimpleSHAVTable[F] {
    override def get(downloadURL: URL, shavtable: URL, predicates: List[String]): F[(File, T)] =
      F.liftIO {
        for {
          _       <- IO.shift(bcs)
          zipfile <- F.toIO(super.get(downloadURL, shavtable, predicates)).map(_._1)
          _       <- IO(scribe.info(s"unzipping ${zipfile.toPath.toAbsolutePath.toString}"))
          _ <- IO {
            if (zipfile.getName.contains("zip")) IO.unit
            else {
              IO.raiseError(new IllegalStateException(s"cannot unzip file without zip substring ${zipfile.getName}"))
            }
          }
          zip <- IO(new ZipFile(zipfile))
          files <- IO {
            zip
              .stream()
              .iterator()
              .asScala
              .toList
              .map(fname =>
                IO {
                  val tmp = File.createTempFile(fname.getName, ".radix")
                  scribe.info(s"unzipping ${fname.getName} into ${tmp.toPath.toAbsolutePath.toString}")
                  scribe.trace(s"the zipfile is ${zipfile.length} bytes")
                  val fout = new FileOutputStream(tmp)
                  fout.getChannel.transferFrom(Channels.newChannel(zip.getInputStream(fname)), 0, Long.MaxValue)
                  fout.flush()
                  fout.close()
                  scribe.info(s"completed unzipping ${fname.getName} into ${tmp.toPath.toAbsolutePath.toString}")
                  scribe.trace(s"the unzipped file is ${tmp.length} bytes")
                  tmp
              })
          }
          allfiles <- Parallel.parSequence(files)
          _ <- if (allfiles.isEmpty) {
            IO.raiseError(new IllegalStateException(s"in the zipfile at $zipfile, there exists no files"))
          } else { IO.unit }
          _ <- if (allfiles.length > 1) {
            IO.raiseError(new IllegalStateException(s"in the zipfile at $zipfile, many files found inside $allfiles"))
          } else { IO.unit }
          _ <- IO.shift
        } yield (allfiles.head, t)
      }
  }

  /**
    * a specific instance where consul is delivered as a zip file
    * @param F the effect instance
    * @tparam F The effect type
    */
  class ConsulSHADL[F[_]](implicit F: Effect[F]) extends ZipSHADL[F] {
    type T = Consul.type
    val t = Consul
  }

  /**
    * A simple instance where nomad is delivered as a zip file
    * @param F the effect instance
    * @tparam F The effect type
    */
  class NomadSHADL[F[_]](implicit F: Effect[F]) extends ZipSHADL[F] {
    type T = Nomad.type
    val t = Nomad
  }

  /**
    * A downloader for Consul and Nomad that uses https://releases.hashicorp.com/ to do it's work
    * @param consulVersion what version of consul
    * @param nomadVersion what version of nomad
    * @param consulpredicates any other consul predicates
    * @param nomadpredicates any other nomad predicates
    * @param F
    * @tparam F
    * @return a file pointing to a downloaded consul and nomad
    */
  def downloadConsulAndNomad[F[_]](
      consulVersion: String,
      nomadVersion: String,
      consulpredicates: List[String],
      nomadpredicates: List[String])(implicit F: Effect[F]): F[((File, Consul.type), (File, Nomad.type))] = F.liftIO {
    val consul = new ConsulSHADL[F]
    val nomad  = new NomadSHADL[F]
    Parallel.parTuple2(
      F.toIO(
        consul.get(
          new URL("https://releases.hashicorp.com/consul/" + consulVersion + "/"),
          new URL(
            "https://releases.hashicorp.com/consul/" + consulVersion + "/" + s"consul_${consulVersion}_SHA256SUMS"),
          consulpredicates
        )),
      F.toIO(
        nomad.get(
          new URL("https://releases.hashicorp.com/nomad/" + nomadVersion + "/"),
          new URL("https://releases.hashicorp.com/nomad/" + nomadVersion + "/" + s"nomad_${nomadVersion}_SHA256SUMS"),
          consulpredicates
        ))
    )
  }
}
