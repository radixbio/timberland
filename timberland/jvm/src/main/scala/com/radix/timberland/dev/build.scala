package com.radix.timberland.dev

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import ammonite.ops._
import ammonite.ops.ImplicitWd._

import cats.effect.{IO, SyncIO}
import cats.implicits._
import cats.effect.ContextShift

import com.radix.timberland.util.Util._

object Build {
    val os_pkg = {
        val arsta = for {
            _ <- IO(%%("""echo "deb https://dl.bintray.com/sbt/debian /" | sudo tee -a /etc/apt/sources.list.d/sbt.list"""))
            _ <- IO(%%("sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823"))
            _ <- IO(%%("sudo apt update"))
            _ <- IO(%%("apt install -y git openjdk-8-jdk sbt"))
        } yield ()
        val prog = IO {
            val pkgs = "git openjdk-8-jdk libz3"
        }
    }

    def submodules(num_cores: Int) = {
        val prog = IO {
            val init = %%('git, "submodule", "init")
            val update = %%('git, "submodule", "update", "-j", num_cores)
            init.out.lines ++ update.out.lines
        }
        for {
            _ <- putStrLn("clone starting")
            out <- prog
            _ <- putStrLn(out)
            _ <- putStrLn("clone complete")
        } yield ()
    }
    
    val Z3 = {
        val prog = {
            val wd: Path = monorepoDir/"third-party"/'ScalaZ3
            val sbt = IO(os.proc('which, 'sbt).call().out.lines.head)
            def cmd(s: String) = IO{os.proc(s, "-Dsbt.log.noformat=true", "+package").call(wd)}
            for {
                s <- sbt
                cmdout <- cmd(s)
                out <- if (cmdout.exitCode != 0)
                    IO.raiseError(new IllegalStateException(s"z3 build failed with exit code ${cmdout.exitCode}"))
                    else {IO.pure(cmdout.out.lines)}
            } yield out
            
        }
        for {
            _ <- putStrLn("Z3 build starting")
            out <- prog
            _ <- putStrLn(out)
            _ <- putStrLn("Z3 build finished")
        } yield ()
    }
    
    def or_tools(num_cores: Int) = {
        val prog: IO[Vector[String]] = {
            val wd: Path =  monorepoDir/"third-party"/"or-tools"

            val make = IO(os.proc("which", "make").call().out.lines.head)
            def runMake(makePath: String, target: String): IO[os.CommandResult] = IO{
                os.proc(makePath, "-j", num_cores, target).call(wd)
            }
            for {
                M <- make
                fstout <- runMake(M, "third_party")
                fstlines <- if (fstout.exitCode != 0)
                    IO.raiseError(new IllegalStateException(s"or-tools third_party build failed with exit code ${fstout.exitCode}"))
                    else {IO.pure(fstout.out.lines)}
                sndout <- runMake(M, "test_java")
                sndlines <- if (sndout.exitCode != 0)
                    IO.raiseError(new IllegalStateException(s"or-tools test_java build failed with exit code ${fstout.exitCode}"))
                    else {IO.pure(sndout.out.lines)}
            } yield fstlines ++ sndlines
        }
        for {
            _ <- putStrLn("or-tools build starting")
            out <- prog
            _ <- putStrLn(out)
            _ <- putStrLn("or-tools build finished")
        } yield ()
    }
}

