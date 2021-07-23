package com.radix.timberland.util

import cats.effect.{ContextShift, IO}
import io.circe.syntax._
import io.circe.parser.parse
import org.http4s.Method.GET
import org.http4s.client.Client
import org.http4s.{Header, Headers, Request, Uri}
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.util.CaseInsensitiveString
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.apache.commons.compress.utils.IOUtils

import sys.process._
import java.net.URL
import java.io.{BufferedInputStream, File, FileInputStream, FileOutputStream}

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

import scala.concurrent.ExecutionContext.Implicits.global

import scala.math.Ordering.Implicits._

object TemplateFiles {
  val config_file = RadPath.runtime / "timberland" / "terraform" / "cloud_upload_config.sh"
  val version_file = RadPath.runtime / "timberland" / "terraform" / "config_version"
  val module_dir = RadPath.runtime / "timberland" / "terraform" / "modules"
  val terraform_exec_dir = RadPath.runtime / "terraform" / ".terraform" / "modules"
}

object UpdateModules {
  case class CloudModule(orgname: String, modname: String, provider: String, api_token: String)

  val ORGNAME = "ORG_NAME=(.*)".r.unanchored
  val MODULE = "MODULE=(.*)".r.unanchored
  val PROVIDER = "PROVIDER=(.*)".r.unanchored
  val API_TOKEN = "API_TOKEN=(.*)".r.unanchored

  def parseConfig(): CloudModule = {
    val lines = os.read(TemplateFiles.config_file).split('\n')

    def _parse(info: CloudModule, line: String): CloudModule = {
      line match {
        case ORGNAME(name)    => info.copy(orgname = name)
        case MODULE(name)     => info.copy(modname = name)
        case PROVIDER(name)   => info.copy(provider = name)
        case API_TOKEN(token) => info.copy(api_token = token)
        case _                => info
      }
    }
    lines.foldLeft(CloudModule(null, null, null, null))(_parse)
  }

  def versionUpToDate(remote_version_string: String): Boolean = {
    if (!os.exists(TemplateFiles.version_file)) {
      false
    } else {
      val local_version_string = os.read(TemplateFiles.version_file).stripMargin

      val threeLayerVersion = "([0-9]*)\\.([0-9]*)\\.([0-9]*)".r
      val local_version = local_version_string match {
        case threeLayerVersion(major, minor, build) => (major.toInt, minor.toInt, build.toInt)
        case _ =>
          throw new RuntimeException(s"Invalid version string in ${TemplateFiles.version_file}: $local_version_string")
      }
      val remote_version = remote_version_string match {
        case threeLayerVersion(major, minor, build) => (major.toInt, minor.toInt, build.toInt)
        case _                                      => throw new RuntimeException(s"Got invalid version string from server: $remote_version_string")
      }

      scribe.info(s"Local version is at $local_version, remote version is at $remote_version")

      local_version >= remote_version
    }
  }

  def getLatestVersion(info: CloudModule, client: Client[IO]): IO[String] = {
    val target_url =
      s"https://app.terraform.io/api/registry/v1/modules/${info.orgname}/${info.modname}/${info.provider}"
    scribe.info(s"trying update: $target_url")
    val auth_header = Header("Authorization", s"Bearer ${info.api_token}")
    val queryReqest = Request[IO](
      method = GET,
      uri = Uri.fromString(target_url).toOption.get,
      headers = Headers.of(auth_header)
    )

    client
      .expect[String](queryReqest)
      .map(parse(_))
      .map(jsn => jsn.getOrElse("None".asJson).hcursor.downField("version").as[String].toOption.get)
  }

  def getDownloadLink(info: CloudModule, version: String, client: Client[IO]): IO[String] = {
    val target_url =
      s"https://app.terraform.io/api/registry/v1/modules/${info.orgname}/${info.modname}/${info.provider}/${version}/download"
    val auth_header = Header("Authorization", s"Bearer ${info.api_token}")
    val queryRequest = Request[IO](
      method = GET,
      uri = Uri.fromString(target_url).toOption.get,
      headers = Headers.of(auth_header)
    )
    client.fetch(queryRequest)(resp => IO.pure(resp.headers.get(CaseInsensitiveString("x-terraform-get")).get.value))
  }

  def downloadModuleTar(url: String, tar_destination: os.Path, client: Client[IO]): IO[Boolean] = {
    val source = new URL(url)
    val out = new File(tar_destination.toString())
    source.#>(out).!!

    val worked = os.exists(tar_destination)
    println(s"Download: $worked")
    IO(os.exists(tar_destination))
  }

  def swap_module(tarfile: String, backup_dir: os.Path): IO[Boolean] =
    IO({
      println(s"Saving prior terraform templates to ${backup_dir.toString()}")
      os.move(TemplateFiles.module_dir, backup_dir)
      os.makeDir(TemplateFiles.module_dir)

      def _extFileFromTar(tar: TarArchiveInputStream): Unit = {
        val entry = tar.getNextEntry()
        if (entry == null) println("tar.gz extraction complete.")
        else {
          val out = new File(TemplateFiles.module_dir.toString(), entry.getName)
          if (entry.isDirectory) {
            print(s"Unpacking subdir ${out.getAbsolutePath}")
            val ret = out.mkdirs()
            println(s": $ret")
          } else {
            println(s"Unpacking file ${out.getAbsolutePath}")
            val outstream = new FileOutputStream(out)
            IOUtils.copy(tar, outstream)
            outstream.close()
          }
          _extFileFromTar(tar)
        }
      }
      val stream_input = new BufferedInputStream(new FileInputStream(tarfile))
      val tar_input = new TarArchiveInputStream(new GzipCompressorInputStream(stream_input))
      try {
        _extFileFromTar(tar_input)
        true
      } catch {
        case error: Throwable => {
          println(s"COULD NOT EXTRACT TAR.GZ ARCHIVE   ${error.toString()} ${error.getMessage}")
          false
        }
      }
    })

  def walkback(module_backup: os.Path): IO[Unit] =
    IO({
      scribe.warn(s"Restoring terraform config from ${module_backup}")
      os.remove(TemplateFiles.module_dir)
      os.move(module_backup, TemplateFiles.module_dir)
      println("Errors occurred during update.  Terraform has been restored to previous configuration.")
    })

  // Terraform fails on replanning if there are fewer module configs in the new
  // main configuration directory than in terraform's working copy in /opt/radix/terraform.
  // This removes extraneous modules from the working copy.
  def auditExecDir(): IO[Unit] =
    IO({
      val module_manifest = os.list(TemplateFiles.module_dir).map(_.baseName)
      val exec_manifest = os.list(TemplateFiles.terraform_exec_dir).map(_.baseName)
      for {
        fn <- exec_manifest.toSet.diff(module_manifest.toSet)
      } yield {
        if (fn == "modules") {
          Unit
        } else {
          println(s"Removing ${TemplateFiles.terraform_exec_dir / fn}")
          os.remove.all(TemplateFiles.terraform_exec_dir / fn)
        }
      }
    })

  // TODO make version specifiable?
  def run(terraformTask: IO[Boolean], namespace: Option[String]): IO[Unit] = {
    val timestamp = DateTimeFormatter.ofPattern("YY-MM-dd-hh-mm").format(LocalDateTime.now())
    val module_backup = os.root / "opt" / "radix" / "timberland" / s"terraform_modules_backup_$timestamp"

    // TODO: This probably doesn't work with nomad namespaces
    val modInfo = parseConfig()

    val tar_file = os.root / "opt" / "radix" / "timberland" / "terraform" / "new_terraform_config.tar"

    implicit val cs: ContextShift[IO] = IO.contextShift(global)
    val update = BlazeClientBuilder[IO](global).resource.use(implicit client =>
      for {
        version <- getLatestVersion(modInfo, client)
        _ <- if (versionUpToDate(version))
          IO(println("Version is up to date."))
        else {
          for {
            download_url <- getDownloadLink(modInfo, version, client)
            downloaded <- downloadModuleTar(download_url, tar_file, client)
            swap_successful <- swap_module(tar_file.toString(), module_backup)
            _ <- auditExecDir()
            ter_successful <- terraformTask
            _ <- if (downloaded && swap_successful && ter_successful)
              IO(os.write.over(TemplateFiles.version_file, version))
            else
              walkback(module_backup)
            _ <- IO(os.remove(tar_file))
          } yield ()
        }
      } yield ()
    )
    update
  }
}
