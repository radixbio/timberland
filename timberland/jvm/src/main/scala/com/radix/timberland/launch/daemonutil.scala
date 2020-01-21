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

/** A holder class for combining a task with it's associated tags that need to be all checked for Daemon Availability
  *
  * @param name The full extended task name which is "{jobName}-{groupName}-{taskName}" for any given task
  * @param tagList A list of type Set[String] which are unique combinations of services that should be checked for
  *                availability
  */
case class TaskAndTags(name: String, tagList: List[Set[String]])

package object daemonutil {
  private[this] implicit val timer: Timer[IO] = IO.timer(global)
  private[this] implicit val cs: ContextShift[IO] = IO.contextShift(global)

  /** A Daemon that can start and stop in Nomad
    *
    * @tparam A The daemon's type
    * @tparam T The type of DaemonState used by the daemon
    */
  trait Daemon[A, T] {
    def quorumCount: Int

//    def daemonName: String

    def assembledDaemon: daemons.Job

    def daemonJob: JobShim

    def service: String

    def tags: Set[String]

    def daemonStarted: T

    val daemonQuorumEstablished: T
    val daemonQuorumNotEstablished: T
  }

  /** The Daemon for Zookeeper
    *
    * @param dev Start with --dev (if available) and other items to make a single node cluster work
    * @param quorumSize Set the expected quorum size
    */
  case class Zookeeper(dev: Boolean, quorumSize: Int)
      extends Daemon[Zookeeper.type, ZookeeperState] {
    val quorumCount: Int = quorumSize
    val assembledDaemon: daemons.Job =
      daemons.ZookeeperDaemons(dev, quorumSize)
//    val zookeeperDaemons = daemons.ZookeeperDaemons(dev, quorumSize)
//    daemons.ZookeeperDaemons.zookeeper.count = quorumSize

//    if (quorumSize == 1) {
//      daemons.ZookeeperDaemons.zookeeper.zookeeper.config.args =
//        (daemons.ZookeeperDaemons.zookeeper.zookeeper.config.args.get :+ "--dev").some
//    }
    val daemonJob: JobShim = assembledDaemon.jobshim
    val daemonName: String = assembledDaemon.name
    val service = "zookeeper-daemons-zookeeper-zookeeper"
    val tags =
      Set("zookeeper-quorum", "zookeeper-client")
    val daemonStarted: ZookeeperState = ZookeeperStarted
    val daemonQuorumEstablished: ZookeeperState = ZookeeperQuorumEstablished
    val daemonQuorumNotEstablished: ZookeeperState =
      ZookeeperQuorumNotEstablished
  }

  /** The Daemon for Kafka
    *
    * @param dev Start with --dev (if available) and other items to make a single node cluster work
    * @param quorumSize Set the expected quorum size
    */
  case class Kafka(dev: Boolean, quorumSize: Int, servicePort: Int = 9092)
      extends Daemon[Kafka.type, KafkaState] {
    val quorumCount: Int = quorumSize
    val assembledDaemon: daemons.Job =
      daemons.KafkaDaemons(dev, quorumSize, servicePort)
    val daemonJob: JobShim = assembledDaemon.jobshim
    val service = "kafka-daemons-kafka-kafka"
    val tags =
      Set("kafka-quorum", "kafka-plaintext")
    val daemonStarted: KafkaState = KafkaTopicCreationStarted
    val daemonQuorumEstablished: KafkaState = KafkaTopicsCreated
    val daemonQuorumNotEstablished: KafkaState = KafkaTopicsFailed
  }

  /** The Daemon for Various Kafka Companions
    *
    * @param dev Start with --dev (if available) and other items to make a single node cluster work
    * @param servicePort The port that Kafka should be listening on on the exposed port (default 9092)
    * @param registryListenerPort The port to set up the Schema Registry to listen on (default: 8081)
    */
  case class KafkaCompanions(dev: Boolean,
                             servicePort: Int = 9092,
                             registryListenerPort: Int = 8081)
      extends Daemon[KafkaCompanions.type, KafkaCompanionState] {
    val quorumCount: Int = 1
    val assembledDaemon: daemons.Job =
      daemons.KafkaCompanionDaemons(dev, servicePort, registryListenerPort)
    val daemonName: String = assembledDaemon.name
    val daemonJob: JobShim = assembledDaemon.jobshim
    val service = "kafka-companion-daemons-kafkaCompanions-kafkaConnect" //TODO: This domain has many services. DO we check just one or check all of them?
    val tags =
      Set("kafka-companion", "kafka-connect")
    val daemonStarted: KafkaCompanionState = KafkaCompanionsStarted
    val daemonQuorumEstablished: KafkaCompanionState =
      KafkaCompanionsQuorumEstablished
    val daemonQuorumNotEstablished: KafkaCompanionState =
      KafkaCompanionsQuorumNotEstablished
  }

  /** The Daemon for Vault
    *
    * @param dev Start with --dev (if available) and other items to make a single node cluster work
    * @param quorumSize Set the expected quorum size
    */
  case class Vault(dev: Boolean, quorumSize: Int)
      extends Daemon[Vault.type, VaultState] {
    val quorumCount: Int = quorumSize
    val assembledDaemon: daemons.Job =
      daemons.VaultDaemon(dev, quorumSize)
    val daemonJob: JobShim = assembledDaemon.jobshim
    val daemonName: String = assembledDaemon.name
    val service = "vault-daemon-vault-vault"
    val tags =
      Set("vault", "vault-listen")
    val daemonStarted: VaultState = VaultStarted
    val daemonQuorumEstablished: VaultState = VaultQuorumEstablished
    val daemonQuorumNotEstablished: VaultState = VaultQuorumNotEstablished
  }

  /** The Daemon for Elasticsearch
    *
    * @param dev Start with --dev (if available) and other items to make a single node cluster work
    * @param quorumSize Set the expected quorum size
    */
  case class Elasticsearch(dev: Boolean, quorumSize: Int)
      extends Daemon[Elasticsearch.type, ESState] {
    val quorumCount: Int = quorumSize
    val assembledDaemon: daemons.Job =
      daemons.Elasticsearch(dev, quorumSize)
    val daemonJob: JobShim = assembledDaemon.jobshim
    val daemonName: String = assembledDaemon.name
    val service = "elasticsearch-elasticsearch-es-general-node"
    val tags =
      Set("elasticsearch", "rest")
    val daemonStarted: ESState = ESStarted
    val daemonQuorumEstablished: ESState = ESQuorumEstablished
    val daemonQuorumNotEstablished: ESState = ESQuorumNotEstablished
  }

  /** The Daemon for Retool
    *
    * @param dev Start with --dev (if available) and other items to make a single node cluster work
    * @param quorumSize Set the expected quorum size
    */
  case class Retool(dev: Boolean, quorumSize: Int)
      extends Daemon[Retool.type, RetoolState] {
    val quorumCount: Int = quorumSize
    val assembledDaemon: daemons.Job =
      daemons.Retool(dev, quorumSize)
    val daemonJob: JobShim = assembledDaemon.jobshim
    val daemonName: String = assembledDaemon.name
    val service = "retool-retool-retool-main"
    val tags =
      Set("retool", "retool-service")
    val daemonStarted: RetoolState = RetoolStarted
    val daemonQuorumEstablished: RetoolState = RetoolQuorumEstablished
    val daemonQuorumNotEstablished: RetoolState = RetoolQuorumNotEstablished
  }

  /** Let a specified function run for a specified period of time before interrupting it and raising an error. This
    *  function sets up the timeoutTo function.
    *
    * Taken from: https://typelevel.org/cats-effect/datatypes/io.html#race-conditions--race--racepair
    *
    * @param fa The function to run (This function must return type IO[A])
    * @param after Timeout after this amount of time
    * @param timer A default Timer
    * @param cs A default ContextShift
    * @tparam A The return type of the function must be IO[A]. A is the type of our result
    * @return Returns the successful completion of the function or a IO.raiseError
    */
  def timeout[A](fa: IO[A], after: FiniteDuration)(
      implicit timer: Timer[IO],
      cs: ContextShift[IO]): IO[A] = {

    val error = new TimeoutException(after.toString)
    timeoutTo(fa, after, IO.raiseError(error))
  }

  /** Creates a race condition between two functions (fa and timer.sleep()) that will let a program run until the timer
    *  expires
    *
    * Taken from: https://typelevel.org/cats-effect/datatypes/io.html#race-conditions--race--racepair
    *
    * @param fa The function to race which must return type IO[A]
    * @param after The duration to let the function run
    * @param fallback The function to run if fa fails
    * @param timer A default timer
    * @param cs A default ContextShift
    * @tparam A The type of our result
    * @return Returns the result of fa if it completes within @after or returns fallback (all IO[A])
    */
  def timeoutTo[A](fa: IO[A], after: FiniteDuration, fallback: IO[A])(
      implicit timer: Timer[IO],
      cs: ContextShift[IO]): IO[A] = {

    IO.race(fa, timer.sleep(after)).flatMap {
      case Left(a)  => IO.pure(a)
      case Right(_) => fallback
    }
  }

  /** Our implicit class that holds functions we want to incorporate into our Daemons
    *
    * @param daemon The daemon being passed to the class and functions
    * @tparam A The daemon's type
    * @tparam T The type of DaemonState used by the daemon
    */
  implicit class DaemonOps[A, T](daemon: Daemon[A, T]) {

    /** Start the daemon. This will check if it is already running and not start if it is. If it isn't, it will go into
      *  a perpetual loop until the Daemon is started.
      *
      * @param interp A Http4sNomadClient to be used for making web requests against Nomad
      * @return Returns an IO[T] where T is the type of DaemonState used by the daemon
      */
    def start(implicit interp: Http4sNomadClient[IO]): IO[T] = {
      for {
        serviceState <- daemon.checkDaemonState(fail = true)
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

    /** Stop the specified daemon (by default this will purge the daemon as well)
      *
      * @param interp A Http4sNomadClient to be used for making web requests against Nomad
      * @return IO[T] where T is a valid DaemonState for the specified service
      */
    def stop(implicit interp: Http4sNomadClient[IO]): IO[T] = {
      for {
        _ <- NomadOp.nomadStopJob(daemon.assembledDaemon.name).foldMap(interp)
        resp <- IO.pure(daemon.daemonQuorumNotEstablished)
      } yield resp
    }

    /** Block and wait until all services across all instances of a specified Daemon are started
      *
      * @return IO[T] where T is a valid DaemonState for the specified service
      */
    def waitForQuorum: IO[T] = {
      for {
        serviceState <- daemon.checkDaemonState()
        res <- if (serviceState == daemon.daemonQuorumEstablished) {
          IO.pure(daemon.daemonQuorumEstablished)
        } else {
          daemon.waitForQuorum
        }
      } yield res
    }

    /** This function compiles the list of task names and associated tags that will need to be checked for daemon
      *  availability
      * @return A list of type TaskAndTags (see above definition)
      */
    def determineTasksAndTags: List[TaskAndTags] = {
      val jobName: String = daemon.assembledDaemon.name
      daemon.assembledDaemon.groups.flatMap(g => {
        g.tasks.map(t => {
          TaskAndTags(name = s"${jobName}-${g.name}-${t.name}",
                      tagList = t.services.map(s => s.tags.toSet))
        })
      })
    }

    /** Check the state of the daemon and its services
      *
      * @param fail Whether the associated consulutil check should propogate (i.e. continuously check) or fail after one
      *             failed check
      * @return Returns an IO[T] where T is the type of DaemonState used by the daemon
      */
    def checkDaemonState(fail: Boolean = false): IO[T] = {
      val servicesAndTags: List[TaskAndTags] = daemon.determineTasksAndTags

      for {
        serviceStates <- servicesAndTags
          .flatMap(st => {
            st.tagList.map(tags => {
              for {
                tasksRunning <- consulutil.waitForService(
                  st.name,
                  tags,
                  daemon.quorumCount,
                  fail)(1.seconds, timer)
                res <- if (tasksRunning.size >= daemon.quorumCount) {
                  IO.pure(daemon.daemonQuorumEstablished)
                } else {
                  IO.pure(daemon.daemonQuorumNotEstablished)
                }

              } yield res
            })
          })
          .parSequence
//        _ <- IO.pure(scribe.debug(s"------${serviceStates.toString}"))
        res <- if (serviceStates.contains(daemon.daemonQuorumNotEstablished)) {
          IO.pure(daemon.daemonQuorumNotEstablished)
        } else {
          IO.pure(daemon.daemonQuorumEstablished)
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

  /** Start up the specified daemons (or all or a combination) based upon the passed parameters. Will continue running
    *  until all specified daemons have successfully started.
    *
    * @param quorumSize How many running and valid copies of a Job Group there should be across the specified services
    * @param dev Whether to run in dev mode. This is used in conjunction with quorumSize to adjust variables sent to
    *            the daemon to start it in development mode
    * @param core Whether to start core services (Zookeeper, Kafka, Kafka Companions)
    * @param vaultStart Whether to start Vault
    * @param esStart Whether to start Elasticsearch
    * @param retoolStart Whether to start retool
    * @param servicePort What port to start Kafka on
    * @param registryListenerPort What port for the Kafka Schema registry to start on
    * @return Returns an IO of DaemonState and since the function is blocking/recursive, the only return value is
    *         AllDaemonsStarted
    */
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

      val zk = Zookeeper(dev, quorumSize)
      val kafka = Kafka(dev, quorumSize, servicePort)
      val kafkaCompanions =
        KafkaCompanions(dev, servicePort, registryListenerPort)
      val es = Elasticsearch(dev, quorumSize)
      val vault = Vault(dev, quorumSize)
      val retool = Retool(dev, quorumSize)

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
                new FiniteDuration(360, duration.SECONDS))
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
