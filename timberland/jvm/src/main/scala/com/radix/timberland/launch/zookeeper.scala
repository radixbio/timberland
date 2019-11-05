package com.radix.timberland.launch

import java.net.InetAddress

import com.radix.timberland.radixdefs._
import cats._
import cats.data._
import cats.effect._
import cats.implicits._
import com.radix.utils.helm._
import ConsulOp.ConsulOpF
import ammonite.ops.Path
import com.radix.utils.helm
import com.radix.utils.helm.http4s._
import org.http4s.Uri
import org.http4s._
import org.http4s.client.blaze._
import org.http4s.client._
import org.http4s.implicits._

import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global
import java.util.concurrent.Executors

import scala.concurrent.duration._
import java.time.Instant
import java.net.NetworkInterface
import os.Shellable
import scala.collection.JavaConverters._

package object zookeeper {
  private [this] implicit val timer = IO.timer(global)
  sealed trait TemplateOps {
    def getTemplate(path: Path): IO[Set[String]] =
      IO(os.read(path).split('\n').filter(_.startsWith("server")).toSet)
  }
  case object NoMinQuorum                                                                extends TemplateOps
  case class MinQuorumFound(servers: Set[String])                                        extends TemplateOps
  case class ZKStarted(servers: Set[String])                                             extends TemplateOps
  case class ZKUpdated(existing: Set[String], toAdd: Set[String], toRemove: Set[String]) extends TemplateOps

  /**
    * This method reads the consul template file to determine if a suitable number of zookeeper servers are present to
    * initiate cluster bootstrap. If it is, it'll return the indication to boot the quorum
    * @param templatePath path to the consul template
    * @return Either a failure to get the servers together to form a quorum, or a list of servers to create ZK with
    */
  private[this] def reconfigDynFile(
      templatePath: Path, minQuorumSize: Int): IndexedStateT[IO, NoMinQuorum.type, Either[NoMinQuorum.type, MinQuorumFound], Unit] = {
    // TODO does querying consul directly work?
    // I couldn't get this to work when I was debugging something, turns out it was something lower in the stack
    // So this may still be viable
    //    for {
    //    state <- IndexedStateT.get[IO, NoMinQuorum.type]
    //    zks <- IndexedStateT.liftF(consulutil.getZKs.value)
    //    res <- zks match {
    //      case Some(quorum) => for {
    //       _ <- IndexedStateT.set[IO, NoMinQuorum.type, Either[NoMinQuorum.type, MinQuorumFound]](Right(MinQuorumFound(quorum.split(",").toSet)))
    //      } yield ()
    //      case None => for {
    //      _ <- IndexedStateT.set[IO, NoMinQuorum.type, Either[NoMinQuorum.type, MinQuorumFound]](Left(state))
    //      } yield ()
    //    }
    //    } yield res


        for {
          state                <- IndexedStateT.get[IO, NoMinQuorum.type]
          templatefilecontents <- IndexedStateT.liftF(state.getTemplate(templatePath))
          _ <- templatefilecontents match {
            case quorum if quorum.size >= minQuorumSize && !quorum.map(_.contains("nil")).reduce(_ || _) =>
              for {
                _ <- IndexedStateT.liftF(IO(scribe.debug(s"found zookeeper quorum, $quorum")))
                _ <- IndexedStateT.set[IO, NoMinQuorum.type, Either[NoMinQuorum.type, MinQuorumFound]](
                  Right(MinQuorumFound(quorum)))
              } yield ()
            case _ =>
              for {
              _ <- IndexedStateT.liftF(IO(scribe.warn(s"template file not in consistent state! $templatefilecontents")))
              res <- IndexedStateT.set[IO, NoMinQuorum.type, Either[NoMinQuorum.type, MinQuorumFound]](Left(NoMinQuorum))
              } yield res
          }

        } yield ()
  }

  /**
    * This method gets the current zookeeper configuration as well as what's rendered by the template to
    * give the information necessary reconfigure zookeeper in a dynamic environment.
    * This only makes sense in ZK 3.5+
    * @param templatePath the path to the consul template file that's being rerendered
    * @return a case class describing how to reconfigure zookeeper's servers
    */
  private[this] def zkreconfigDynFile(templatePath: Path): IndexedStateT[IO, ZKStarted, ZKUpdated, Unit] =
    //TODO: Actually move to zookeeper 3.5+ to make this work.
    for {
      state    <- IndexedStateT.get[IO, ZKStarted]
      template <- IndexedStateT.liftF(state.getTemplate(templatePath))
      _        <- IndexedStateT.liftF(IO(scribe.trace("getting current zookeeper configuration")))
      existing <- IndexedStateT.liftF(
        IO(
          new String(os.proc("zkCli.sh", "config").call().out.bytes)
            .split('\n')
            .filter(_.startsWith("server"))
            .toSet))
      _ <- IndexedStateT.liftF(IO(scribe.trace(s"got current zookeeper configuration $existing")))
      _ <- IndexedStateT.set[IO, ZKStarted, ZKUpdated](
        ZKUpdated(existing, template.diff(existing), existing.diff(template)))
    } yield ()

  /**
    * This method only makes sense in ZK 3.5+ with dynamic reconfiguration
    * @return a reconfigured zookeeper with the additional nodes added and removed
    */
  private[this] def applyZKStateUpdate: IndexedStateT[IO, ZKUpdated, ZKStarted, Unit] =
    //TODO: Actually move to zookeeper 3.5+ to make this work.
    for {
      state <- IndexedStateT.get[IO, ZKUpdated]
      res <- IndexedStateT.liftF(for {
        _ <- if (state.toAdd.nonEmpty) {
          IO {
            scribe.trace(s"dynamically reconfiguring zookeeper to add ${state.toAdd}")
            os.proc("zkCli.sh", "reconfig", "-add", os.Shellable(state.toAdd.toSeq)).call()
          }
        } else { IO.unit }
        _ <- if (state.toRemove.nonEmpty) {
          IO {
            scribe.trace(s"dynamically reconfiguring zookeeper to remove ${state.toRemove}")
            os.proc("zkCli.sh", "reconfig", "-remove", os.Shellable(state.toRemove.toSeq)).call()
          }
        } else { IO.unit }
      } yield ZKStarted(servers = state.existing.union(state.toAdd).diff(state.toRemove)))
      _ <- IndexedStateT.set[IO, ZKUpdated, ZKStarted](res)
    } yield ()

  /**
    * This method actually starts zookeeper once the minimum number of servers is in the quorum
    * @param zoocfg the path to the zookeeper configuration file
    * @param zoodyncfg the path to the zookeeper dynamic configuration file
    * @return The state is now with zookeeper started
    */
  private[this] def zookeeperQuorumStart(zoocfg: Path,
                                         zoodyncfg: Path)(implicit N: LocalEthInfoAlg[IO]): IndexedStateT[IO, MinQuorumFound, ZKStarted, Unit] = {
    for {
      quorum <- IndexedStateT.get[IO, MinQuorumFound]
      _ <- IndexedStateT.liftF(IO {
        scribe.trace(s"creating zookeeper dynamic reconfiguration file with quorum: $quorum")
        os.write.append(zoocfg, quorum.servers.toList.mkString("\n"))
        val datadir =
          Path(os.read(zoocfg).split('\n').filter(_.startsWith("dataDir")).head.drop("dataDir=".length))

        //This truly ugly code is to extract the current node's ID from the output of the consul template
        //TODO replace this with a parser that errors if the template doesn't match
        //This is dependent on a consul-template being exactly as it is :(

        val iface = N.getNetworkInterfaces.unsafeRunSync().toSet
        //network interfaces that are also listed as ZK servers allow us to infer our iteration order and recover an ID
          .intersect(quorum.servers.map(_.split('=')).map(_.flatMap(_.split(':'))).map(_(1)))
          .head

        //same here

        val myid = quorum.servers
          .filter(_.contains(iface))
          .head
          .split('.')
          .flatMap(_.split('='))
          .toList(1)
          .toInt
        scribe.trace(s"my zookeeper id is $myid")
        os.write(datadir / 'myid, myid.toString)
      })
      _ <- IndexedStateT.liftF(IO(scribe.trace("starting zookeeper...")))
      _ <- IndexedStateT.liftF(IO {
        os.proc("zkServer.sh", "start-foreground")
          .spawn(ammonite.ops.root, stdout = os.Inherit, stderr = os.Inherit)
      })
      _ <- IndexedStateT.liftF(IO(scribe.trace(s"zookeeper started with $quorum")))
      _ <- IndexedStateT.set[IO, MinQuorumFound, ZKStarted](ZKStarted(quorum.servers))
    } yield ()
  }

  /**
    * This method is the main runner for starting zookeeper. It recurses until a quorum is determined, then boots zookeeper
    * @param templatePath the path to the rerendered consul template
    * @param zooconf the path to the zookeeper static configuration file
    * @param zoodynconf the path to the zookeeper dynamic configuration file
    * @return a started zookeeper
    */
  def startZookeeper(templatePath: Path,
                     zooconf: Path,
                     zoodynconf: Path,
                     minQuorumSize: Int): IndexedStateT[IO, NoMinQuorum.type, ZKStarted, Unit] = {
    implicit val netinfo = new NetworkInfoExec[IO]
    for {
      _     <- reconfigDynFile(templatePath, minQuorumSize)
      state <- IndexedStateT.get[IO, Either[NoMinQuorum.type, MinQuorumFound]]
      _ <- state match {
        case Right(minquorum) =>
          for {
            _       <- IndexedStateT.set[IO, Either[NoMinQuorum.type, MinQuorumFound], MinQuorumFound](minquorum)
            _       <- zookeeperQuorumStart(zooconf, zoodynconf)
            started <- IndexedStateT.get[IO, ZKStarted]
            _       <- IndexedStateT.set[IO, ZKStarted, ZKStarted](started)
          } yield ()
        case Left(noquorum) =>
          for {
            _ <- IndexedStateT.set[IO, Either[NoMinQuorum.type, MinQuorumFound], NoMinQuorum.type](noquorum)
            _ <- IndexedStateT.liftF(IO.sleep(1.second))
            _ <- startZookeeper(templatePath, zooconf, zoodynconf, minQuorumSize)
          } yield ()
      }
    } yield ()
  }
}
