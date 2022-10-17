package com.radix.timberland.runtime

import java.net.InetAddress

import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.radix.timberland.launch.{ServiceAddrs, daemonutil}
import com.radix.utils.helm
import com.radix.utils.helm.{ConsulOp, QueryResponse}
import com.radix.utils.helm.http4s.Http4sConsulClient
import io.circe.{Decoder, Json}
import io.circe.parser.{decode, parse}
import io.circe.syntax._
import org.http4s.Uri
import org.http4s.implicits._
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext.Implicits.global

sealed trait FlagUpdateResponse
case class ConsulFlagsUpdated(flags: Map[String, Boolean]) extends FlagUpdateResponse
case class FlagsStoredLocally() extends FlagUpdateResponse

case class ModuleDefinition(key: String, source: String, dir: String)

object flags {
  private implicit val cs: ContextShift[IO] = IO.contextShift(global)
  private implicit val moduleDecoder: Decoder[ModuleDefinition] =
    Decoder.forProduct3("Key", "Source", "Dir")(ModuleDefinition.apply)

  private val flagFile = os.rel / "terraform" / "flags.json"

  // A list of flags which don't have any relation to modules
  private val specialFlags = Set("dev", "no_restart")
  // A map from flag name to a list of module names
  private val flagSupersets = Map(
    "core" -> Set(
      "apprise", "kafka", "kafka_companions", "minio", "retool_pg_kafka_connector",
      "retool_postgres", "vault", "zookeeper"
    )
  )
  // The default values for all flags
  private val defaultFlagMap = resolveSupersetFlags(Map(
    "core" -> true,
    "dev" -> true
  ))
  // All flags that aren't tied to a specific module
  private val nonModuleFlags = specialFlags ++ flagSupersets.keySet + "all"

  /**
   * A map from feature flag to a list of services associated with that flag
   * This is used by the waitForQuorum function to determine
   * when features have finished starting up
   */
  val flagServiceMap = Map(
    "core" -> Vector(
      "zookeeper-daemons-zookeeper-zookeeper",
      "kafka-companion-daemons-kafkaCompanions-kSQL",
      "kafka-companion-daemons-kafkaCompanions-kafkaConnect",
      "kafka-companion-daemons-kafkaCompanions-kafkaRestProxy",
      "kafka-companion-daemons-kafkaCompanions-schemaRegistry",
      "kafka-daemons-kafka-kafka",
      "minio-job-minio-group-nginx-minio",
      "apprise-apprise-apprise",
    ),
    "yugabyte" -> Vector(
      "yugabyte-yugabyte-ybmaster",
      "yugabyte-yugabyte-ybtserver",
    ),
    "es" -> Vector(
      "elasticsearch-elasticsearch-es-generic-node",
      "elasticsearch-kibana-kibana",
    ),
    "retool" -> Vector(
      "retool-retool-postgres",
      "retool-retool-retool-main",
    ),
    "elemental" -> Vector(
      "elemental-machines-em-em",
    )
    // "vault" -> Vector(
    //   "vault-daemon-vault-vault",
    // ),
  )

  /**
   * Sets a feature flag either in Consul or in the local flag file (if Consul is not running)
   * If Consul is up, this also pushes pending changes in the local flag file. If flags is empty,
   * this function only pushes the pending changes.
   * @param persistentDir Timberland directory. Usually /opt/radix/timberland
   * @param flags A map of new flags to push
   * @return If Consul is up, the current state of all feature flags
   */
  def updateFlags(persistentDir: os.Path,
                  flagsToSet: Map[String, Boolean] = Map.empty,
                  serviceAddrs: ServiceAddrs = ServiceAddrs()): IO[FlagUpdateResponse] = {
    for {
      validFlags <- validateFlags(persistentDir, flagsToSet)
      actualFlags = resolveSupersetFlags(flagsToSet, validFlags)
      consulDnsResponse <- IO(InetAddress.getAllByName(serviceAddrs.consulAddr)).attempt
      newFlags <- consulDnsResponse match {
        case Left(_) | Right(Array()) =>
          setLocalFlags(persistentDir, actualFlags) *>
          IO.pure(FlagsStoredLocally())
        case _ =>
          for {
            localFlags <- getLocalFlags(persistentDir)
            _ <- clearLocalFlagFile(persistentDir, localFlags)
            newFlags <- setConsulFlags(localFlags ++ actualFlags, serviceAddrs)
          } yield ConsulFlagsUpdated(newFlags)
      }
    } yield newFlags
  }

  def getConsulFlags(serviceAddrs: ServiceAddrs = ServiceAddrs()): IO[Map[String, Boolean]] = {
    BlazeClientBuilder[IO](global).resource.use { client =>
      val consulUri = Uri.fromString(s"http://${serviceAddrs.consulAddr}:8500").toOption.get
      val interpreter = new Http4sConsulClient[IO](consulUri, client)
      val getFeaturesOp = ConsulOp.kvGetJson[Map[String, Boolean]]("features", None, None)
      for {
        features <- helm.run(interpreter, getFeaturesOp)
      } yield features match {
        case Right(QueryResponse(Some(consulFlagMap), _, _, _)) =>
          defaultFlagMap ++ consulFlagMap
        case _ =>
          defaultFlagMap
      }
    }
  }

  /**
   * Pushes a map of flags to Consul
   * @param flags The flags to push
   * @return The total set of all flags on Consul after pushing
   */
  private def setConsulFlags(flags: Map[String, Boolean],
                             serviceAddrs: ServiceAddrs = ServiceAddrs()): IO[Map[String, Boolean]] = {
    BlazeClientBuilder[IO](global).resource.use { client =>
      val consulUri = Uri.fromString(s"http://${serviceAddrs.consulAddr}:8500").toOption.get
      val interpreter = new Http4sConsulClient[IO](consulUri, client)
      val setFeaturesOp = ConsulOp.kvSetJson("features", _: Map[String, Boolean])
      for {
        curFlags <- getConsulFlags(serviceAddrs)
        _ <- helm.run(interpreter, setFeaturesOp(curFlags ++ flags))
      } yield curFlags ++ flags
    }
  }

  /**
   * Reads the local flag file to get the "dev" setting along with any pending feature flag changes
   * @param persistentDir Timberland directory. Usually /opt/radix/timberland
   * @return A map of flags present in the local flag file
   */
  def getLocalFlags(persistentDir: os.Path): IO[Map[String, Boolean]] = {
    val flagFileLocation = flagFile resolveFrom persistentDir
    for {
      flagFileExists <- IO(os.exists(flagFileLocation))
      flagFileText <- if (flagFileExists) IO(os.read(flagFileLocation)) else IO.pure("")
    } yield {
      decode[Map[String, Boolean]](flagFileText).getOrElse(Map.empty)
    }
  }

  /**
   * Store a set of feature flags locally on the disk. These will be pushed to Consul next time
   * timberland connects to Consul
   * @param persistentDir Timberland directory. Usually /opt/radix/timberland
   * @param flags The flags to set in the local flag file
   * @return The updated contents of the flag file
   */
  private def setLocalFlags(persistentDir: os.Path, flags: Map[String, Boolean]): IO[Unit] = {
    for {
      oldFlags <- getLocalFlags(persistentDir)
      newFlags = oldFlags ++ flags
      newFlagsJson = newFlags.asJson.toString()
      _ <- IO(os.write.over(flagFile resolveFrom persistentDir, newFlagsJson))
    } yield ()
  }

  /**
   * Removes all flags except the dev flag from the local file. This is so that if another computer
   * changes the feature flags on Consul, starting timberland on the original computer won't overwrite them
   * @param persistentDir Timberland directory. Usually /opt/radix/timberland
   * @param contents Current contents of the local flag file (before clearing)
   * @return Nothing
   */
  private def clearLocalFlagFile(persistentDir: os.Path, contents: Map[String, Boolean]): IO[Unit] = {
    if (contents.nonEmpty) {
      // Persist any special flags so they can be retrieved before consul is started
      val newJson = specialFlags.map(flag => flag -> {
        val defaultSpecialFlagVal = defaultFlagMap.getOrElse(flag, false)
        contents.getOrElse(flag, defaultSpecialFlagVal)
      }).toMap.asJson.asString

      IO(os.write.over(flagFile resolveFrom persistentDir, newJson))
    } else IO.unit
  }

  /**
   * Throws an error if any of the flags specified in `flags` is invalid
   * @param persistentDir Timberland directory. Usually /opt/radix/timberland
   * @return The total list of valid flags that was validated against
   */
  private def validateFlags(persistentDir: os.Path, flags: Map[String, Boolean]): IO[Set[String]] = {
    for {
      validFlags <- getValidFlags(persistentDir)
    } yield {
      val invalidFlags = flags.keySet -- validFlags -- nonModuleFlags
      if (invalidFlags.isEmpty) validFlags else {
        scribe.error("Invalid flags specified: " + invalidFlags.mkString(", "))
        sys.exit(1)
      }
    }
  }

  /**
   * Auto-detects a list of valid flags, each corresponding with a terraform module or a member of nonModuleFlags
   * @param persistentDir Timberland directory. Usually /opt/radix/timberland
   * @return A list of valid flags
   */
  private def getValidFlags(persistentDir: os.Path): IO[Set[String]] = {
    val moduleFile = persistentDir / os.up / "terraform" / ".terraform" / "modules" / "modules.json"
    for {
      _ <- daemonutil.initTerraform(false, false)
      modulesText <- IO(os.read(moduleFile))
    } yield {
      val modulesJson = parse(modulesText).getOrElse(Json.Null)
      modulesJson.hcursor.get[List[ModuleDefinition]]("Modules").toOption.get
        .map { case ModuleDefinition(key, _, _) => key }
        .filter(name => name.nonEmpty)
        .toSet
    }
  }

  /**
   *
   * @param flagsToSet A list of changes to apply (e.g. all -> true)
   * @param validFlags A set of valid flags. Used when "all"
   * @return The resolved list of variables to change (e.g. kafka -> true, etc)
   */
  private def resolveSupersetFlags(flagsToSet: Map[String, Boolean],
                                   validFlags: Set[String] = Set.empty): Map[String, Boolean] = {
    val supersetsWithAll = flagSupersets + ("all" -> validFlags)
    flagsToSet -- supersetsWithAll.keys ++ supersetsWithAll.toList.flatMap {
      case (supersetFlagName, flagSet) if flagsToSet contains supersetFlagName =>
        flagSet.map(_ -> flagsToSet(supersetFlagName))
      case _ => Map.empty
    }.toMap
  }
}
