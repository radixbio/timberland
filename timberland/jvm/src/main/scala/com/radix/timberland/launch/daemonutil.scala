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

case object ZookeeperQuorumNotEstablished
    extends ZookeeperState
    with DaemonNotRunning

case object KafkaStarted extends KafkaState
case object KafkaQuorumEstablished extends KafkaState with DaemonRunning
case object KafkaQuorumNotEstablished extends KafkaState with DaemonNotRunning

case object KafkaTopicCreationStarted extends KafkaState
case object KafkaTopicsCreated extends KafkaState
case object KafkaTopicsFailed extends KafkaState

case object KafkaCompanionsStarted extends KafkaCompanionState
case object KafkaCompanionsQuorumEstablished
    extends KafkaCompanionState
    with DaemonRunning
case object KafkaCompanionsQuorumNotEstablished
    extends KafkaCompanionState
    with DaemonNotRunning

sealed trait VaultState extends DaemonState
case object VaultStarted extends VaultState
case object VaultQuorumEstablished extends VaultState with DaemonRunning
case object VaultQuorumNotEstablished extends VaultState with DaemonNotRunning

sealed trait ESState extends DaemonState
case object ESStarted extends ESState
case object ESQuorumEstablished extends ESState with DaemonRunning
case object ESQuorumNotEstablished extends ESState with DaemonNotRunning

sealed trait RetoolState extends DaemonState
case object RetoolStarted extends RetoolState
case object RetoolQuorumEstablished extends RetoolState with DaemonRunning
case object RetoolQuorumNotEstablished extends RetoolState with DaemonNotRunning

case object AllDaemonsStarted extends DaemonState

package object daemonutil {
  private[this] implicit val timer: Timer[IO] = IO.timer(global)
  private[this] implicit val cs: ContextShift[IO] = IO.contextShift(global)

  trait Daemon[A, T] {
    def quorumCount: Int

//    def daemonName: String

    def daemonJob: JobShim

    def service: String

    def tags: Set[String]

    def daemonStarted: T

    val daemonQuorumEstablished: T
    val daemonQuorumNotEstablished: T
  }

  case class Zookeeper(startInDevMode: Boolean, quorumSize: Int)
      extends Daemon[Zookeeper.type, ZookeeperState] {
    val quorumCount: Int = quorumSize
    val zookeeperDaemons = daemons.ZookeeperDaemons(startInDevMode, quorumSize)
//    daemons.ZookeeperDaemons.zookeeper.count = quorumSize

//    if (quorumSize == 1) {
//      daemons.ZookeeperDaemons.zookeeper.zookeeper.config.args =
//        (daemons.ZookeeperDaemons.zookeeper.zookeeper.config.args.get :+ "--dev").some
//    }
    val daemonJob: JobShim = zookeeperDaemons.jobshim
    val daemonName: String = zookeeperDaemons.name
    val service = "zookeeper-daemons-zookeeper-zookeeper"
    val tags =
      Set("zookeeper-quorum", "zookeeper-client")
    val daemonStarted: ZookeeperState = ZookeeperStarted
    val daemonQuorumEstablished: ZookeeperState = ZookeeperQuorumEstablished
    val daemonQuorumNotEstablished: ZookeeperState =
      ZookeeperQuorumNotEstablished
  }

  case class Kafka(startInDevMode: Boolean,
                   quorumSize: Int,
                   servicePort: Int = 9092)
      extends Daemon[Kafka.type, KafkaState] {
    val quorumCount: Int = quorumSize
    val kafkaDaemons =
      daemons.KafkaDaemons(startInDevMode, quorumSize, servicePort)
//    val daemonName: String = kafkaDaemons.name
//    kafkaDaemons.kafka.count = quorumSize
//    if (quorumSize == 1) {
//      kafkaDaemons.kafka.kafkaTask.config.args =
//        (kafkaDaemons.kafka.kafkaTask.config.args.get :+ "--dev").some
//      kafkaDaemons.kafka.kafkaTask.env =
//        (kafkaDaemons.kafka.kafkaTask.env.get ++ Map(
//          "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR" -> "1")).some
//    }
    val daemonJob: JobShim = kafkaDaemons.jobshim
    val service = "kafka-daemons-kafka-kafka"
    val tags =
      Set("kafka-quorum", "kafka-plaintext")
    val daemonStarted: KafkaState = KafkaTopicCreationStarted
    val daemonQuorumEstablished: KafkaState = KafkaTopicsCreated
    val daemonQuorumNotEstablished: KafkaState = KafkaTopicsFailed
  }

  case class KafkaCompanions(startInDevMode: Boolean,
                             servicePort: Int = 9092,
                             registryListenerPort: Int = 8081)
      extends Daemon[KafkaCompanions.type, KafkaCompanionState] {
    val quorumCount: Int = 1
    val kafkaCompanionDaemons = daemons.KafkaCompanionDaemons(
      startInDevMode,
      servicePort,
      registryListenerPort)
    val daemonName: String = kafkaCompanionDaemons.name
    val daemonJob: JobShim = kafkaCompanionDaemons.jobshim
    val service = "kafka-companion-daemons-kafkaCompanions-kafkaConnect" //TODO: This domain has many services. DO we check just one or check all of them?
    val tags =
      Set("kafka-companion", "kafka-connect")
    val daemonStarted: KafkaCompanionState = KafkaCompanionsStarted
    val daemonQuorumEstablished: KafkaCompanionState =
      KafkaCompanionsQuorumEstablished
    val daemonQuorumNotEstablished: KafkaCompanionState =
      KafkaCompanionsQuorumNotEstablished
  }

  case class Vault(startInDevMode: Boolean, quorumSize: Int)
      extends Daemon[Vault.type, VaultState] {
    val quorumCount: Int = quorumSize
    val vaultDaemons = daemons.VaultDaemon(startInDevMode, quorumSize)
    val daemonJob: JobShim = vaultDaemons.jobshim
    val daemonName: String = vaultDaemons.name
    val service = "vault-daemon-vault-vault"
    val tags =
      Set("vault", "vault-listen")
    val daemonStarted: VaultState = VaultStarted
    val daemonQuorumEstablished: VaultState = VaultQuorumEstablished
    val daemonQuorumNotEstablished: VaultState = VaultQuorumNotEstablished
  }

  case class Elasticsearch(startInDevMode: Boolean, quorumSize: Int)
      extends Daemon[Elasticsearch.type, ESState] {
    val quorumCount: Int = quorumSize
    val esDaemons = daemons.Elasticsearch(startInDevMode, quorumSize)
    val daemonJob: JobShim = esDaemons.jobshim
    val daemonName: String = esDaemons.name
    val service = "elasticsearch-elasticsearch-es-general-node"
    val tags =
      Set("elasticsearch", "rest")
    val daemonStarted: ESState = ESStarted
    val daemonQuorumEstablished: ESState = ESQuorumEstablished
    val daemonQuorumNotEstablished: ESState = ESQuorumNotEstablished
  }

  case class Retool(startInDevMode: Boolean, quorumSize: Int)
      extends Daemon[Retool.type, RetoolState] {
    val quorumCount: Int = quorumSize
    val retoolDaemons = daemons.Retool(startInDevMode, quorumSize)
    val daemonJob: JobShim = retoolDaemons.jobshim
    val daemonName: String = retoolDaemons.name
    val service = "retool-retool-retool-main"
    val tags =
      Set("retool", "retool-service")
    val daemonStarted: RetoolState = RetoolStarted
    val daemonQuorumEstablished: RetoolState = RetoolQuorumEstablished
    val daemonQuorumNotEstablished: RetoolState = RetoolQuorumNotEstablished
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
        _ <- IO.pure(scribe.info(
          s"COMPLETED INITIAL SERVICE CHECK FOR ${daemon.getClass.getSimpleName}"))
        res <- if (serviceState == daemon.daemonQuorumEstablished) {
          IO.pure(scribe.info(
            s"QUORUM FOUND DURING INITIAL SERVICE CHECK FOR ${daemon.getClass.getSimpleName}"))
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

//    def stop(implicit interp: Http4sNomadClient[IO]): IO[T] = {
//      for {
//        _ <- NomadOp.nomadStopJob(daemon.daemonName).foldMap(interp)
//        resp <- IO.pure(daemon.daemonQuorumNotEstablished)
//      } yield resp
//    }

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
        tasksRunning <- consulutil.waitForService(daemon.service,
                                                  daemon.tags,
                                                  daemon.quorumCount,
                                                  fail)(1.seconds, timer)
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

  def waitForQuorum(quorumSize: Int,
                    dev: Boolean,
                    core: Boolean,
                    vaultStart: Boolean,
                    esStart: Boolean,
                    retoolStart: Boolean,
                    servicePort: Int,
                    registryListenerPort: Int): IO[DaemonState] = {

    BlazeClientBuilder[IO](global).resource.use(implicit client => {
      scribe.info("Checking Nomad Quorum...")

      implicit val interp: Http4sNomadClient[IO] =
        new Http4sNomadClient[IO](uri("http://nomad.service.consul:4646"),
                                  client)
      val startInDevMode: Boolean = quorumSize match {
        case 1 => true
        case _ => false
      }

      val zk = Zookeeper(startInDevMode, quorumSize)
      val kafka = Kafka(startInDevMode, quorumSize, servicePort)
      val kafkaCompanions =
        KafkaCompanions(startInDevMode, servicePort, registryListenerPort)
      val es = Elasticsearch(startInDevMode, quorumSize)
      val vault = Vault(startInDevMode, quorumSize)
      val retool = Retool(startInDevMode, quorumSize)

      val result = for {
        nomadQuorumStatus <- timeout(Nomad.waitForQuorum(interp, quorumSize),
                                     new FiniteDuration(2, duration.MINUTES))
        coreStatus <- core match {
          case true => {
            for {

              zkStart <- zk.start
              zkQuorumStatus <- timeout(
                zk.waitForQuorum,
                new FiniteDuration(60, duration.SECONDS))
              kafkaStart <- kafka.start
              kafkaQuorumStatus <- timeout(
                kafka.waitForQuorum,
                new FiniteDuration(60, duration.SECONDS))
              kafkaCompanionStart <- kafkaCompanions.start
              kafkaCompanionQuorumStatus <- timeout(
                kafkaCompanions.waitForQuorum,
                new FiniteDuration(120, duration.SECONDS))
            } yield kafkaCompanionQuorumStatus
          }
          case false => IO.sleep(1.second)
        }
        vaultQuorumStatus <- vaultStart match {
          case true => {
            for {
              vaultStart <- vault.start
              vaultQuorumStatus <- timeout(
                vault.waitForQuorum,
                new FiniteDuration(60, duration.SECONDS))
            } yield vaultQuorumStatus
          }
          case false => IO.sleep(1.second)
        }
        retoolQuorumStatus <- retoolStart match {
          case true => {
            for {
              retoolStart <- retool.start
              retoolQuorumStatus <- timeout(
                retool.waitForQuorum,
                new FiniteDuration(360, duration.SECONDS))
            } yield retoolQuorumStatus
          }
          case false => IO.sleep(1.second)
        }
        esQuorumStatus <- esStart match {
          case true => {
            for {
              esStart <- es.start
              esQuorumStatus <- timeout(
                es.waitForQuorum,
                new FiniteDuration(120, duration.SECONDS))
            } yield esQuorumStatus
          }
          case false => IO.sleep(1.second)
        }

      } yield esQuorumStatus
      result.attempt.flatMap {
        case Left(a) =>
          a.printStackTrace()
          timeout(waitForQuorum(quorumSize,
                                dev,
                                core,
                                vaultStart,
                                esStart,
                                retoolStart,
                                servicePort,
                                registryListenerPort),
                  new FiniteDuration(10, duration.MINUTES))
        case Right(a) =>
          IO.pure(AllDaemonsStarted)

      }
    })
  }

//  def stopAllServices(): IO[DaemonState] = {
//    BlazeClientBuilder[IO](global).resource.use(implicit client => {
//      scribe.info("Checking Nomad Quorum...")
//
//      implicit val interp: Http4sNomadClient[IO] =
//        new Http4sNomadClient[IO](uri("http://nomad.service.consul:4646"),
//          client)
//      val zk = Zookeeper(1)
//      val kafka = Kafka(1)
//      val kafkaCompanions = KafkaCompanions(1)
//      val es = Elasticsearch(1)
//      val vault = Vault(1)
//
//      for {
////        _ <- zk.stop
////        _ <- kafka.stop
////        _ <- kafkaCompanions.stop
////        _ <- es.stop
////        vaultStatus <- vault.stop
//      } yield vaultStatus
//    })
//  }

  case object Nomad {
    def waitForQuorum(implicit interp: Http4sNomadClient[IO],
                      quorumSize: Int): IO[QuorumState] =
      waitForNoamdQuorum
  }
}
