package com.radix.timberland.runtime

//import ammonite._
//import ammonite.ops._
import cats._
import cats.effect._
import cats.implicits._
import java.io.{File, FileOutputStream}
import java.nio.channels.Channels
import java.nio.charset.Charset
import java.nio.file.{CopyOption, Files, StandardCopyOption}

import scala.collection.mutable.ListBuffer
import com.radix.timberland.radixdefs._

import java.util.jar.JarFile

object Installer {

  /*
  This true monstrosity is due to the fact that it's impossible to extract a directory recursively
  from a java resource.
   */
  class MoveFromJVMResources[F[_]](implicit F: Effect[F]) extends FunctionalCopy[F] {
    scribe.trace("initializing jvm class mover")
    private[this] def copyResourceToFolder(resname: String, name: String, to: os.Path, options: CopyOption*): Unit = {
      val is = Thread.currentThread().getContextClassLoader.getResourceAsStream(resname)
      val f = new File(to + "/" + name)
      f.getParentFile.mkdirs()
      val fout = new FileOutputStream(f)
      fout.getChannel.transferFrom(Channels.newChannel(is), 0, Long.MaxValue)
      fout.flush()
      fout.close()
    }
    override def fncopy(from: os.Path, to: os.Path): F[Unit] = F.delay {
      scribe.info(s"copying $from to $to")
      val fromstream = Thread.currentThread().getContextClassLoader.getResourceAsStream(from.toString().drop(1))
      scribe.info(s"fromstream ${fromstream.available()}")
      if (fromstream.available() == 0) {
        val jarFile = new File(getClass.getProtectionDomain.getCodeSource.getLocation.getPath)
        val toCopy = if (jarFile.isFile) { // Run with JAR file
          val jar = new JarFile(jarFile)
          val entries = jar.entries //gives ALL entries in jar
          val lb = new ListBuffer[String]
          while ({
            entries.hasMoreElements
          }) {
            val name = entries.nextElement.getName
            if (name.startsWith(from.toString().drop(1) + "/") && !name.endsWith("/")) { //filter according to the path
              lb.append(name)
            }
          }
          jar.close()
          lb.toList
        } else List.empty[String]
        toCopy.foreach(r => scribe.info(s"preparing to move (from resource directory $from) $r to $to"))
        if (toCopy.isEmpty) scribe.warn(s"the directory $from is empty")
        toCopy.foreach(r =>
          copyResourceToFolder(
            r,
            r.replaceAllLiterally(from.toString.drop(1) + "/", ""),
            to,
            StandardCopyOption.REPLACE_EXISTING
          )
        )
      } else {
        copyResourceToFolder(
          from.toString().drop(1),
          from.segments.toList.last,
          to,
          StandardCopyOption.REPLACE_EXISTING
        )
      }

      ()
    }
  }

}
