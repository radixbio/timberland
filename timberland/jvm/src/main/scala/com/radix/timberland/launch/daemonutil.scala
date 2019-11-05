package com.radix.timberland.launch

import java.nio.channels.UnresolvedAddressException

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

sealed trait QuorumState

case object NoNomadQuorum extends QuorumState

case object NomadQuorumEstablished extends QuorumState

sealed trait DaemonState

sealed trait ZookeeperState extends DaemonState

sealed trait KafkaState extends DaemonState

sealed trait KafkaCompanionState extends DaemonState

case object ZookeeperStarted extends ZookeeperState

case object ZookeeperQuorumEstablished extends ZookeeperState

case object ZookeeperQuorumNotEstablished extends ZookeeperState

case object KafkaStarted extends KafkaState
case object KafkaQuorumEstablished extends KafkaState
case object KafkaQuorumNotEstablished extends KafkaState

case object KafkaTopicCreationStarted extends KafkaState
case object KafkaTopicsCreated extends KafkaState
case object KafkaTopicsFailed extends KafkaState

case object KafkaCompanionsStarted extends KafkaCompanionState
case object KafkaCompanionsQuorumEstablished extends KafkaCompanionState

package object daemonutil {
  private[this] implicit val timer: Timer[IO] = IO.timer(global)
  private[this] implicit val cs: ContextShift[IO] = IO.contextShift(global)

  trait Daemon[A] {
    def quorumCount: Int

    def daemonJob: JobShim

    def service: String

    def tags: Set[String]

    def daemonStarted: DaemonState

    def daemonQuorumEstablished: DaemonState
  }

  case class Zookeeper(implicit quorumSize: Int)
      extends Daemon[Zookeeper.type] {
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
  }

  case class Kafka(implicit quorumSize: Int) extends Daemon[Kafka.type] {
    val quorumCount: Int = quorumSize
    daemons.KafkaDaemons.kafka.count = quorumSize
    if (quorumSize === 1) {
      daemons.KafkaDaemons.kafka.kafkaTask.config.args =
        (daemons.KafkaDaemons.kafka.kafkaTask.config.args.get :+ "--dev").some
      daemons.KafkaDaemons.kafka.kafkaTask.env = (daemons.KafkaDaemons.kafka.kafkaTask.env.get ++ Map("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR" -> "1")).some
    }
    val daemonJob: JobShim = daemons.KafkaDaemons.jobshim
    val service = "kafka-daemons-kafka-kafka"
    val tags =
      Set("kafka-quorum", "kafka-plaintext")
    val daemonStarted: KafkaState = KafkaTopicCreationStarted
    val daemonQuorumEstablished: KafkaState = KafkaTopicsCreated
  }

  case class KafkaCompanions(implicit quorumSize: Int) extends Daemon[Kafka.type] {
    val quorumCount: Int = quorumSize
    if (quorumSize === 1) {
      val newEnv = (daemons.KafkaCompanionDaemons.kafkaCompainions.kafkaConnect.env.get ++ daemons.KafkaCompanionDaemons.kafkaCompainions.kafkaConnect.devEnv).some
      daemons.KafkaCompanionDaemons.kafkaCompainions.kafkaConnect.env = newEnv
    } else {
      daemons.KafkaCompanionDaemons.kafkaCompainions.kafkaConnect.env = Some(daemons.KafkaCompanionDaemons.kafkaCompainions.kafkaConnect.env.get ++ daemons.KafkaCompanionDaemons.kafkaCompainions.kafkaConnect.prodEnv)
    }
    val daemonJob: JobShim = daemons.KafkaCompanionDaemons.jobshim
    val service = "kafka-companion-daemons-kafkaCompanions-kafkaConnect" //TODO: This domain has many services. DO we check just one or check all of them?
    val tags =
      Set("kafka-companion", "kafka-connect")
    val daemonStarted: KafkaCompanionState = KafkaCompanionsStarted
    val daemonQuorumEstablished: KafkaCompanionState =
      KafkaCompanionsQuorumEstablished
  }

  implicit class DaemonOps[A](daemon: Daemon[A]) {
    def start(implicit interp: Http4sNomadClient[IO]): IO[DaemonState] = {
      for {
        jobResponse <- NomadOp
          .nomadCreateJobFromHCL(job = daemon.daemonJob)
          .foldMap(interp)
        _ <- IO(
          scribe.info(s"Started job for ${daemon.getClass.getSimpleName}"))
        res <- IO.pure(ZookeeperStarted)
      } yield res
    }

    def waitForQuorum(implicit client: Client[IO]): IO[DaemonState] = {
      for {
        _ <- IO.sleep(15.seconds)
        tasksRunning <- consulutil.waitForService(daemon.service, daemon.tags, daemon.quorumCount)(10.seconds, timer)
        res <- if (tasksRunning.size >= daemon.quorumCount) {
          IO.pure(daemon.daemonQuorumEstablished)
        } else {
          daemon.waitForQuorum
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
      _ <- IO.sleep(10.seconds)
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
      IO.sleep(30.second) *> {
        scribe.info("Checking Nomad Quorum in 30 seconds...")

        implicit val interp: Http4sNomadClient[IO] =
          new Http4sNomadClient[IO](uri("http://nomad.service.consul:4646"),
                                    client)
        val zk = Zookeeper()
        val kafka = Kafka()
        val kafkaCompanions = KafkaCompanions()
        for {
          nomadQuorumStatus <- Nomad.waitForQuorum
          zkStart <- zk.start
          zkQuorumStatus <- zk.waitForQuorum
          kafkaStart <- kafka.start
          kafkaQuorumStatus <- kafka.waitForQuorum
          kafkaCompanionStart <- kafkaCompanions.start
          kafkaCompanionQuorumStatus <- kafkaCompanions.waitForQuorum
        } yield kafkaCompanionQuorumStatus

      }

    })
  }

  case object Nomad {
    def waitForQuorum(implicit interp: Http4sNomadClient[IO],
                      quorumSize: Int): IO[QuorumState] =
      waitForNoamdQuorum
  }
}
