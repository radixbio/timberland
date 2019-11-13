package com.radix.timberland.launch

import java.nio.channels.UnresolvedAddressException

import akka.remote.WireFormats.TimeUnit
import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import com.radix.timberland.daemons
import com.radix.utils.helm.NomadHCL.syntax.JobShim
import com.radix.utils.helm.http4s.Http4sNomadClient
import com.radix.utils.helm.{NomadOp, NomadReadRaftConfigurationResponse}
import org.http4s.Uri.uri
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.TimeoutException
import scala.concurrent.duration

sealed trait QuorumState

case object NoNomadQuorum extends QuorumState

case object NomadQuorumEstablished extends QuorumState

sealed trait DaemonState
sealed trait DaemonRunning extends DaemonState
sealed trait DaemonNotRunning extends DaemonState

sealed trait ZookeeperState extends DaemonState

sealed trait KafkaState extends DaemonState

sealed trait KafkaCompanionState extends DaemonState

case object ZookeeperStarted extends ZookeeperState

case object ZookeeperQuorumEstablished extends ZookeeperState with DaemonRunning

case object ZookeeperQuorumNotEstablished extends ZookeeperState with DaemonNotRunning

case object KafkaStarted extends KafkaState
case object KafkaQuorumEstablished extends KafkaState with DaemonRunning
case object KafkaQuorumNotEstablished extends KafkaState with DaemonNotRunning

case object KafkaTopicCreationStarted extends KafkaState
case object KafkaTopicsCreated extends KafkaState
case object KafkaTopicsFailed extends KafkaState

case object KafkaCompanionsStarted extends KafkaCompanionState
case object KafkaCompanionsQuorumEstablished extends KafkaCompanionState with DaemonRunning
case object KafkaCompanionsQuorumNotEstablished extends KafkaCompanionState with DaemonNotRunning

case object AllDaemonsStarted extends DaemonState

package object daemonutil {
  private[this] implicit val timer: Timer[IO] = IO.timer(global)
  private[this] implicit val cs: ContextShift[IO] = IO.contextShift(global)

  trait Daemon[A, T] {
    def quorumCount: Int

    def daemonJob: JobShim

    def service: String

    def tags: Set[String]

    def daemonStarted: T

    val daemonQuorumEstablished: T
    val daemonQuorumNotEstablished: T
  }

  case class Zookeeper(implicit quorumSize: Int)
      extends Daemon[Zookeeper.type, ZookeeperState] {
    val quorumCount: Int = quorumSize
    daemons.ZookeeperDaemons.zookeeper.count = quorumSize
    if (quorumSize === 1) {
      daemons.ZookeeperDaemons.zookeeper.zookeeper.config.args =
        (daemons.ZookeeperDaemons.zookeeper.zookeeper.config.args.get :+ "--dev").some

    }
    val daemonJob: JobShim = daemons.ZookeeperDaemons.jobshim
    val service = "zookeeper-daemons-zookeeper-zookeeper"
    val tags =
      Set("zookeeper-quorum", "zookeeper-client")
    val daemonStarted: ZookeeperState = ZookeeperStarted
    val daemonQuorumEstablished: ZookeeperState = ZookeeperQuorumEstablished
    val daemonQuorumNotEstablished: ZookeeperState = ZookeeperQuorumNotEstablished
  }

  case class Kafka(implicit quorumSize: Int) extends Daemon[Kafka.type, KafkaState] {
    val quorumCount: Int = quorumSize
    daemons.KafkaDaemons.kafka.count = quorumSize
    if (quorumSize === 1) {
      daemons.KafkaDaemons.kafka.kafkaTask.config.args =
        (daemons.KafkaDaemons.kafka.kafkaTask.config.args.get :+ "--dev").some
      daemons.KafkaDaemons.kafka.kafkaTask.env =
        (daemons.KafkaDaemons.kafka.kafkaTask.env.get ++ Map(
          "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR" -> "1")).some
    }
    val daemonJob: JobShim = daemons.KafkaDaemons.jobshim
    val service = "kafka-daemons-kafka-kafka"
    val tags =
      Set("kafka-quorum", "kafka-plaintext")
    val daemonStarted: KafkaState = KafkaTopicCreationStarted
    val daemonQuorumEstablished: KafkaState = KafkaTopicsCreated
    val daemonQuorumNotEstablished: KafkaState = KafkaTopicsFailed
  }

  case class KafkaCompanions(implicit quorumSize: Int)
      extends Daemon[KafkaCompanions.type, KafkaCompanionState] {
    val quorumCount: Int = quorumSize
    if (quorumSize === 1) {
      val newEnv =
        (daemons.KafkaCompanionDaemons.kafkaCompainions.kafkaConnect.env.get ++ daemons.KafkaCompanionDaemons.kafkaCompainions.kafkaConnect.devEnv).some
      daemons.KafkaCompanionDaemons.kafkaCompainions.kafkaConnect.env = newEnv
    } else {
      daemons.KafkaCompanionDaemons.kafkaCompainions.kafkaConnect.env = Some(
        daemons.KafkaCompanionDaemons.kafkaCompainions.kafkaConnect.env.get ++ daemons.KafkaCompanionDaemons.kafkaCompainions.kafkaConnect.prodEnv)
    }
    val daemonJob: JobShim = daemons.KafkaCompanionDaemons.jobshim
    val service = "kafka-companion-daemons-kafkaCompanions-kafkaConnect" //TODO: This domain has many services. DO we check just one or check all of them?
    val tags =
      Set("kafka-companion", "kafka-connect")
    val daemonStarted: KafkaCompanionState = KafkaCompanionsStarted
    val daemonQuorumEstablished: KafkaCompanionState =
      KafkaCompanionsQuorumEstablished
    val daemonQuorumNotEstablished: KafkaCompanionState = KafkaCompanionsQuorumNotEstablished
  }

  def timeoutTo[A](fa: IO[A], after: FiniteDuration, fallback: IO[A])(
      implicit timer: Timer[IO],
      cs: ContextShift[IO]): IO[A] = {

    IO.race(fa, timer.sleep(after)).flatMap {
      case Left(a)  => IO.pure(a)
      case Right(_) => fallback
    }
  }

  def timeout[A](fa: IO[A], after: FiniteDuration)(
      implicit timer: Timer[IO],
      cs: ContextShift[IO]): IO[A] = {

    val error = new TimeoutException(after.toString)
    timeoutTo(fa, after, IO.raiseError(error))
  }

  implicit class DaemonOps[A, T](daemon: Daemon[A, T]) {
    def start(implicit interp: Http4sNomadClient[IO]): IO[T] = {
      for {
        serviceState <- daemon.checkServiceState(fail = true)
        _ <- IO.pure(scribe.info(s"COMPLETED INITIAL SERVICE CHECK FOR ${daemon.getClass.getSimpleName}"))
        res <- if (serviceState == daemon.daemonQuorumEstablished) {
          IO.pure(scribe.info(s"QUORUM FOUND DURING INITIAL SERVICE CHECK FOR ${daemon.getClass.getSimpleName}"))
          IO.pure(daemon.daemonQuorumEstablished)
        } else {
          for {
            jobResponse <- NomadOp
              .nomadCreateJobFromHCL(job = daemon.daemonJob)
              .foldMap(interp)
            _ <- IO(
              scribe.info(s"Started job for ${daemon.getClass.getSimpleName}"))
            res <- IO.pure(daemon.daemonStarted)
          } yield res
        }
//        res <- serviceState match {
//          case _: DaemonNotRunning => IO.pure(daemon.daemonStarted)
//          case _: DaemonRunning => {
//            for {
//              jobResponse <- NomadOp
//                .nomadCreateJobFromHCL(job = daemon.daemonJob)
//                .foldMap(interp)
//              _ <- IO(
//                scribe.info(s"Started job for ${daemon.getClass.getSimpleName}"))
//              res <- IO.pure(daemon.daemonStarted)
//            } yield res
//          }
//        }
      } yield res
    }

    def waitForQuorum: IO[T] = {
      for {
        serviceState <- daemon.checkServiceState()
        res <- if (serviceState == daemon.daemonQuorumEstablished) {
          IO.pure(daemon.daemonQuorumEstablished)
        } else {
          daemon.waitForQuorum
        }
      } yield res
    }

    def checkServiceState(fail: Boolean = false): IO[T] = {
      for {
        tasksRunning <- consulutil.waitForService(
          daemon.service,
          daemon.tags,
          daemon.quorumCount, fail)(1.seconds, timer)
        res <- if (tasksRunning.size >= daemon.quorumCount) {
          IO.pure(daemon.daemonQuorumEstablished)
        } else {
          IO.pure(daemon.daemonQuorumNotEstablished)
        }
      } yield res
    }
  }

  def checkNomadState(implicit interp: Http4sNomadClient[IO])
    : IO[NomadReadRaftConfigurationResponse] = {
    for {
      state <- NomadOp.nomadReadRaftConfiguration().foldMap(interp)
    } yield state
  }

  def waitForNoamdQuorum(implicit interp: Http4sNomadClient[IO],
                         quorumSize: Int): IO[QuorumState] = {
    for {
      _ <- IO.sleep(1.seconds)
      state <- checkNomadState
      serverLeader = state.servers.map(_.leader).find(_.equals(true))
      res <- if (state.servers.length >= quorumSize && serverLeader.getOrElse(
                   false)) {
        IO.pure(NomadQuorumEstablished)
      } else {
        waitForNoamdQuorum
      }
    } yield res
  }

  def waitForQuorum(implicit quorumSize: Int): IO[DaemonState] = {

    BlazeClientBuilder[IO](global).resource.use(implicit client => {
      scribe.info("Checking Nomad Quorum...")

      implicit val interp: Http4sNomadClient[IO] =
        new Http4sNomadClient[IO](uri("http://nomad.service.consul:4646"),
                                  client)
      val zk = Zookeeper()
      val kafka = Kafka()
      val kafkaCompanions = KafkaCompanions()(1)



      val result = for {
        nomadQuorumStatus <- timeout(Nomad.waitForQuorum,
          new FiniteDuration(2, duration.MINUTES))
        zkStart <- zk.start
        zkQuorumStatus <- timeout(zk.waitForQuorum,
                                  new FiniteDuration(60, duration.SECONDS))
        kafkaStart <- kafka.start
        kafkaQuorumStatus <- timeout(kafka.waitForQuorum,
                                     new FiniteDuration(60, duration.SECONDS))
        kafkaCompanionStart <- kafkaCompanions.start
        kafkaCompanionQuorumStatus <- timeout(
          kafkaCompanions.waitForQuorum,
          new FiniteDuration(60, duration.SECONDS))
      } yield kafkaCompanionQuorumStatus
      result.attempt.flatMap {
        case Left(a) =>
          timeout(waitForQuorum, new FiniteDuration(10, duration.MINUTES))
        case Right(a) =>
          IO.pure(AllDaemonsStarted)

      }
    })
  }

  case object Nomad {
    def waitForQuorum(implicit interp: Http4sNomadClient[IO],
                      quorumSize: Int): IO[QuorumState] =
      waitForNoamdQuorum
  }
}
