package com.radix.timberland.util

import java.net.{InetAddress, UnknownHostException}
import java.nio.charset.StandardCharsets
import java.util.concurrent.{ExecutorService, Executors}

import cats.effect._
import cats.implicits._
import com.radix.timberland.launch.daemonutil
import com.radix.utils.tls._
import fs2.Stream
import io.circe.Json
import io.circe.generic.auto._
import io.circe.jawn.CirceSupportParser
import io.circe.optics.JsonPath.root
import jawnfs2.JsonStreamSyntax
import org.fusesource.jansi.{Ansi, AnsiConsole}
import org.http4s._
import org.http4s.circe.jsonDecoder
import org.http4s.client.blaze._
import org.http4s.implicits._
import org.typelevel.jawn.Facade
import scribe.LogRecord
import scribe.format._
import scribe.output.LogOutput
import scribe.writer.Writer

import scala.collection.mutable
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

/** TODO
 * - fix: hashistack status lines mangling external reports and DNS; no DNS check for consul
 * - ideally padding should adjust to terminal size to avoid line wrapping
 * - context shifts could be more idiomatic
 * - blaze should log to scribe
 * - potentially reconsider StatusUpdate heirarchy, the types get ugly
 * - restructure scribe to use different log levels (and add documentation about what's what)
 */
// desiderata:
// maybe flags of both --verbose or something like --consul-requests=DEBUG --terraform=INFO
// documentation for what is included in each level of verbosity
// status of terraform; command being run; levels of verbosity about the args' commands
// the existing job/nomad status/services/health checks/dns
// "making request X, calling function Y"

case class LogTUIWriter() extends Writer {
  // consider: colour log for terminal but also optionally to a file
  // variety of debug topics and views. verbose logging to a separate file by default
  val format = formatter"$time [$threadName] $level $position - $message$mdc" // not sure if this is best for general use
  // the best i've got for formatter docs is: https://github.com/outr/scribe/blob/master/core/shared/src/main/scala/scribe/format/package.scala
  // see also FormatBlocks, Formatter, and ../utils/Abbreviator... i would prefer just the last part of the class name

  override def write[M](record: LogRecord[M], output: LogOutput): Unit = {
    LogTUI.writeLog(output.plainText)
  }
}

object Investigator {
  private val pool: ExecutorService = Executors.newCachedThreadPool
  val investigatorContextExecutor: ExecutionContextExecutor = ExecutionContext.fromExecutor(pool)
  implicit val investigatorCS: ContextShift[IO] = IO.contextShift(investigatorContextExecutor)
  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  // ideally fall back to TrustEveryoneSSlContext.sslContext if can't open cert files
  val unboundedClientBuilder: BlazeClientBuilder[IO] = BlazeClientBuilder[IO](
    investigatorContextExecutor,
    Some(TrustEveryoneSSLContext.sslContext)
  ).withCheckEndpointAuthentication(false)
  val statusUpdateQueue: mutable.Queue[StatusUpdate] = mutable.Queue.empty[StatusUpdate]
  private val investigationsInProgress: concurrent.Ref[IO, Int] = concurrent.Ref.of[IO, Int](0).unsafeRunSync()

  /** Sleep until all investigations in progress finish (i.e. DNS resolves for all services) * */
  def waitForInvestigations(): IO[Unit] =
    investigationsInProgress.get.flatMap(x => if (x == 0) IO.unit else IO.sleep(2.seconds) *> waitForInvestigations())

  /**
   * Report a Hashistack status change.
   *
   * @param service     : Consul, Vault, Nomad
   * @param status      : Waiting, Starting, Waiting for Systemd, Service started, Service running and DNS resolves
   * @param description : optional parenthetical
   */
  def reportHashiUpdate(service: String, status: String, description: String = ""): IO[Unit] =
    report(HashistackStatus(service, status, description))

  /**
   * Report a status update to be displayed.
   *
   * @param update one or more status updates
   * @return
   */
  def report(update: StatusUpdate*): IO[Unit] = IO {
    update.foreach(u => scribe.info(s"update: $u")) // wish this would have the caller's context for scribe
    statusUpdateQueue.enqueue(update: _*)
  }

  def genericWaitForDNS(
                         service: String,
                         timeoutDuration: FiniteDuration,
                         reportDNS: (String, String) => IO[Unit]
                       ): IO[Boolean] = {
    def retry: IO[Boolean] = IO.sleep(2.seconds) *> genericWaitForDNS(service, timeoutDuration - 2.seconds, reportDNS)

    if (timeoutDuration <= 0.seconds) {
      reportDNS("Error", "Timed out") *> IO {
        false
      }
    } else
      for {
        _ <- reportDNS("DNS Resolving...", s"$timeoutDuration remaining")
        lookupResult <- IO {
          InetAddress.getAllByName(s"$service.service.consul")
        }.attempt
        success <- lookupResult match {
          case Left(_: UnknownHostException) => retry
          case Left(err: Throwable) => reportDNS("DNS Error", err.toString) *> IO {
            false
          }
          case Right(addresses: Array[InetAddress]) =>
            addresses match {
              case Array() => reportDNS("Empty", "DNS lookup resolved with empty results") *> retry
              case _ => reportDNS("DNS Resolved", "") *> IO {
                true
              }
            }
        }
      } yield success
  }

  /** Wait for a DNS record to become available. Consul will not return a record for failing services.
   *
   * @param service The Consul service to look up
   * @param timeout How long to wait before throwing an exception
   */
  def waitForService(service: String, timeout: FiniteDuration): IO[Boolean] = {
    genericWaitForDNS(service, timeout, (status, desc) => report(HashistackStatus(service, status, desc)))
    // needs to do DNS Resolved -> Service Started and DNS Resolved
  }

  /**
   * Makes a streaming request to Nomad's event stream API for the deployment topic
   * Parses events as they are received and turns them into JobStatuses.
   * Unsafely, asynchronously investigates each JobStatus by reporting it; if the job is successful,
   * getting health checks for the job's services; if those pass, trying to resolve DNS for those services.
   * Pro-tip: If you want to get more information from the event stream, you can use scripts/explore_nomad_events.py to
   * figure out what's available
   *
   * @param token : ACL token for Nomad
   */
  def investigateEverything(token: String): IO[Fiber[IO, Unit]] = {
    val ourExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))
    val ourContextShift: ContextShift[IO] = IO.contextShift(ourExecutionContext)
    val ourClientBuilder: BlazeClientBuilder[IO] = BlazeClientBuilder[IO](
      ourExecutionContext,
      Some(ConsulVaultSSLContext.sslContext)
    )(IO.ioConcurrentEffect(ourContextShift)).withCheckEndpointAuthentication(false)

    def request(url: Uri): Request[IO] =
      Request[IO](
        Method.GET,
        url,
        headers = Headers.of(Header("X-Nomad-Token", token), Header("X-Consul-Token", token))
      )

    implicit val get: String => IO[Json] =
      (url: String) => unboundedClientBuilder.resource.use(_.expect[Json](request(Uri.unsafeFromString(url))))
    // Logger(logBody = true, logHeaders = true)(blaze).use ... // send to scribe.trace

    implicit val facade: Facade[Json] = new CirceSupportParser(None, false).facade
    val jobStatusReceiver = for {
      client <- ourClientBuilder.stream
      jsonChunk <- client
        .stream(request(uri"https://nomad.service.consul:4646/v1/event/stream?topic=Deployment"))
        .flatMap(_.body.chunks.parseJsonStream)
        .handleErrorWith(_ => Stream(Json.Null))
      jobStatus <- Stream(root.Events.each.Payload.Deployment.as[JobStatus].getAll(jsonChunk): _*)
    } yield (IO.shift(investigatorCS) *> investigate(jobStatus)).start(investigatorCS).unsafeRunAsyncAndForget()
    IO.shift(ourContextShift) *> jobStatusReceiver.compile.drain.start(ourContextShift)
  }

  /**
   * Report a StatusUpdate for this JobStatus.
   * If Nomad has reported the job as successful, try to get the job's service names from Nomad.
   * For each service, try to get its health checks from Consul. Report the status of each check.
   * If each health check succeeds, try to resolve DNS for each service.
   * Report each DNS status until DNS resolves or a 5 minute timeout
   *
   * @param jobStatus : Deployment event from the nomad event stream
   */
  def investigate(jobStatus: JobStatus)(implicit get: String => IO[Json]): IO[Unit] = {
    val shouldInvestigate = jobStatus.Status == "successful"

    def waitForJobService(service: JobStatus#ServiceStatus, duration: FiniteDuration): IO[Boolean] = {
      genericWaitForDNS(
        service.serviceName,
        duration,
        (status: String, desc: String) => report(service.copy(status = status, statusDescription = desc))
      )
    }

    val investigation = for {
      _ <- investigationsInProgress.modify(x => (x + 1, x))
      services <- getServiceNamesForJob(jobStatus)
      _ <- report(services: _*)
      checks <- services.parTraverse(getHealthChecksForService).map(_.flatten)
      _ <- report(checks: _*)
      skipDNS = !checks.forall(check => check.Status == "passing")
      dnsResults <- if (skipDNS) IO(Seq(false)) else services.parTraverse(name => waitForJobService(name, 10.minutes))
      _ <- if (dnsResults.forall(identity))
        report(jobStatus.copy(Status = "Successful", StatusDescription = "all checks pass"))
      else {
//        get(s"job/${jobId}/allocations")
//        get("client/fs/logs/{alloc}?task={task???}&type=stderr&plain=true")
        IO.unit
      } // would be cool to hide child keys
      _ <- investigationsInProgress.modify(x => (x - 1, x))
    } yield ()
    report(jobStatus) *> (if (shouldInvestigate) investigation else IO.unit)
  }

  def getServiceNamesForJob(job: JobStatus)(implicit get: String => IO[Json]): IO[List[JobStatus#ServiceStatus]] =
    for {
      jobInfo <- get(s"https://nomad.service.consul:4646/v1/job/${job.JobID}")
      names = root.TaskGroups.each.Services.each.Name.string.getAll(jobInfo)
      statuses = names.map(name => job.ServiceStatus(name, "fetching health checks from consul"))
      _ = if (statuses.isEmpty) {
        report(job.ServiceStatus("fetch service names", "failure", "couldn't parse any service names from nomad"))
        scribe.info(s"couldn't parse any service names from nomad for ${job.jobName}")
      }
    } yield statuses

  def getHealthChecksForService(
                                 service: JobStatus#ServiceStatus
                               )(implicit get: String => IO[Json]): IO[List[JobStatus#ServiceStatus#HealthCheckStatus]] =
    for {
      checks <- get(s"https://consul.service.consul:8501/v1/health/checks/${service.serviceName}")
      _ = scribe.info(s"${!checks.isNull}: got a response for consul/health/checks${service.serviceName}")
    } yield root.each.as[service.HealthCheckStatus].getAll(checks)
}

trait StatusLine {val key: String; def toString: String; def render: String}

case class StyleLine(key: String, render: String, override val toString: String = "") extends StatusLine

abstract class StatusUpdate extends StatusLine {
  def helpRender(
                  name: String,
                  nameStyle: String,
                  status: String,
                  statusStyler: Map[String, String],
                  maybeDesc: String = ""
                ): String = {
    val styledName = if (nameStyle.isEmpty) name else s"@|$nameStyle $name|@"
    val padding = "-" * (50 - name.length)
    val statusStyle = statusStyler.getOrElse(status.capitalize, "")
    val styledStatus = if (statusStyle.isEmpty) status.capitalize else s"@|$statusStyle ${status.capitalize}|@"
    val desc: String = if (maybeDesc.nonEmpty) s" ($maybeDesc)" else ""
    val styledLine = s"$styledName: @|faint $padding|@ $styledStatus $desc"
    new Ansi().render(styledLine).toString
  }

}

case class HashistackStatus(service: String, status: String, statusDescription: String) extends StatusUpdate {
  override val toString = s"$service: $status $statusDescription"
  val key: String = "1" + service.capitalize
  private val styleMap = Map(
    "Waiting" -> "bold",
    "Service Started" -> "green",
    "Service started and DNS resolved" -> "green",
    "DNS resolved" -> "green",
    "Is active" -> "green"
  )
  val render: String = helpRender(service.capitalize, "", status, styleMap, statusDescription)
}

case class JobStatus(JobID: String, Status: String, StatusDescription: String) extends StatusUpdate { // hideChildren parameter?
  private val prefix = daemonutil.getPrefix(false)
  val jobName: String = if (JobID.startsWith(prefix)) JobID.slice(prefix.length, JobID.length) else JobID
  override val toString = s"$jobName job status $Status, ($StatusDescription)"
  val key: String = jobName
  val spinner =  "⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏" // https://github.com/helloIAmPau/node-spinner/blob/master/spinners.json
  def icon(): String =
    if (StatusDescription == "all checks pass") "✓" else spinner(((System.currentTimeMillis() / 500) % spinner.length).toInt).toString

  def render: String = {
    helpRender(s"${icon()} $jobName", "bold", Status, Map("Running" -> "bold", "Successful" -> "green"), StatusDescription)
  }
  case class ServiceStatus(serviceName: String, status: String, statusDescription: String = "") extends StatusUpdate {
    override val toString = s"$jobName service $serviceName: $status ($statusDescription)"
    val key = s"$jobName-$serviceName"
    val styleMap = Map("DNS Resolved" -> "green", "Fetching health checks from consul" -> "faint")

    val render: String = helpRender(" " * 4 + serviceName, "", status, styleMap, statusDescription)

    case class HealthCheckStatus(Name: String, Status: String) extends StatusUpdate {
      override val toString = s"$serviceName health check $Name: $Status"
      val key: String = s"$jobName-$serviceName-$Name"
      val render: String = helpRender(" " * 8 + Name, "", Status, Map("Passing" -> "green", "Critical" -> "red"))
    }
  }
}

object LogTUI {
  val tuiExecutionContext: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))
  implicit val tuiTimer: Timer[IO] = IO.timer(tuiExecutionContext)
  implicit val tuiContextShift: ContextShift[IO] = IO.contextShift(tuiExecutionContext)
  private val screen =  concurrent.MVar[IO].of(()).unsafeRunSync()
  private val denouement = mutable.Queue.empty[String]
  var isActive = false

  def acquireScreen(): IO[Unit] = screen.take

  def releaseScreen(): IO[Unit] = screen.put(())

  def ansi = new Ansi()

  private val blank = " " * 80
  private val separator = "#" * 80

  val startingState = List(
    StyleLine("0", separator),
    StyleLine("0.1", blank), // HashiUpdate keys start with 1
    StyleLine("2", blank),
    StyleLine("3", separator),
    StyleLine("4", blank)
  )

  /** *
   * Turns on the LogTUI display.
   *
   * NOTE - While the LogTUI is active, all terminal output should either be through LogTUI.printAfter()
   * or acquireScreen *> print *> releaseScreen
   * so that text-overwriting or lack-of-overwriting doesn't produce confusing displays
   * */
  def startTUI(consulExists: Boolean = false, nomadExists: Boolean = false, vaultExists: Boolean = false): IO[Unit] = {
    if (isActive) IO.unit else for {
      _ <- IO {isActive = true}
      _ <- Investigator.reportHashiUpdate("Consul", if(consulExists) "Is active" else "Waiting")
      _ <- Investigator.reportHashiUpdate("Nomad", if(nomadExists) "Is active" else "Waiting")
      _ <- Investigator.reportHashiUpdate("Vault", if(vaultExists) "Is active" else "Waiting")
      _ <- IO.shift
      _ = AnsiConsole.systemInstall()
      _ = print(ansi.eraseScreen().saveCursorPosition())
      _ = os.write.over(os.root / "tmp" / "timberland_status_updates", "new run\n")
      _ <- renderStatuses(startingState).start
    } yield ()
  }

  /*
   * Stop rendering, print messages stored in calls to LogTUI.printAfter()
   * */
  def endTUI(error: Option[Throwable] = None): IO[Unit] =
    for {
      wasActive <- IO.pure(isActive)
      _ = if (wasActive) writeLog("shutting down TUI")
      _ <- IO {isActive = false}
      _ <- if (error.isEmpty) IO.sleep(2.seconds)(IO.timer(global)) else IO.unit
      _ <- IO {if (wasActive) {
          Console.flush()
          denouement.foreach(println)
          println()
          error match {
            case None => println("Complete")
            case Some(definitely_error) =>
              println("Encountered Errors")
              println("Startup hit exception:")
              definitely_error.printStackTrace(System.out)
              sys.exit(1)
          }
        } else denouement.foreach(println)
      }
    } yield ()

  /**
   * Empty the status update queue, keep the latest status update for each status key, print them in the right order.
   * @param prevStatuses
   * @param tick
   * @return
   */
  def renderStatuses(prevStatuses: List[StatusLine], tick: Int = 0): IO[Unit] = {
    val getMergedStatuses = IO {
      val newStatuses = Investigator.statusUpdateQueue.dequeueAll(_ => true).toList
      newStatuses.foreach(s => os.write.append(os.root / "tmp" / "timberland_status_updates", s.toString + "\n"))
      (prevStatuses ++ newStatuses)
        .groupBy(_.key)
        .map(_._2.last) // keep the most recent status for each distinct identifier
        .toList
        .sortBy(_.key)
    }
    for {
      mergedStatuses <- getMergedStatuses
      _ <- acquireScreen()
      _ = print(ansi.eraseScreen(Ansi.Erase.BACKWARD).restoreCursorPosition().cursor(0, 0)) // crazy VT100 hack
      // if you had just done cursor(0, 0).saveCursorPosition, this won't work
      // this way you have a drawing surface between the saved cursor position at the bottom and the top of the screen
      // the alternative to Erase.BACKWARDS (which can sometimes erase more than expected) is .eraseLine before each rendered line
      _ = println(ansi.render(mergedStatuses.map(_.render).mkString("\n")))
      _ = println("." * (1 + tick % 3))
      _ <- releaseScreen()
      _ <- IO.sleep(500.milliseconds)
      _ <- if (isActive) renderStatuses(mergedStatuses, tick + 1) else IO.unit
    } yield ()
  }


  def writeLog(output: String): Unit = {
    if (!isActive) println(output)
    os.write.append(RadPath.runtime / "timberland.log", output + "\n")
  }

  def writeLogFromStream(stream: Array[Byte], len: Int): Unit = {
    val str = new String(stream.take(len), StandardCharsets.UTF_8)
    writeLog(str)
  }

  def vault(stream: Array[Byte], len: Int): Unit = writeLogFromStream(stream, len)


  def stdErrs(source: String)(stream: Array[Byte], len: Int): Unit = {
    val str = new String(stream.take(len), StandardCharsets.UTF_8)
    printAfter(s"stderr from $source:\t$str")
  }

  /**
   * Collects print statements during LogTUI execution to be shown after the TUI display is terminated.
   * Use this for things like generated tokens or status output that the user will need to have post-startup.
   * */
  def printAfter(str: String): Unit = {
    denouement.enqueue(str)
    writeLog(str)
  }
}