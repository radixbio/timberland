package com.radix.timberland.util

import java.io.{PrintWriter, StringWriter}
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

import cats.effect.{IO, _}
import cats.effect.concurrent.{MVar, MVar2}
import cats.implicits._
import com.radix.timberland.util.LogTUI.Printer.{AlreadyResponding, Missing, HashistackStates, ServiceState}
import com.radix.timberland.util.TerraformMagic.TerraformPlan
import org.fusesource.jansi.{Ansi, _}
import org.fusesource.jansi.internal.CLibrary.{isatty, STDOUT_FILENO}
import scribe.LogRecord
import scribe.output.LogOutput
import scribe.writer.Writer

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

case class LogTUIWriter() extends Writer {
  override def write[M](record: LogRecord[M], output: LogOutput): Unit = {
    LogTUI.writeLog(output.plainText)
  }
}


// Hashistack consists of Services with Events and States

sealed trait HashistackEvent
//TODO up/down
case object ConsulStarting extends HashistackEvent
case object NomadStarting extends HashistackEvent
case object VaultStarting extends HashistackEvent
case object ConsulSystemdUp extends HashistackEvent
case object NomadSystemdUp extends HashistackEvent
case object VaultSystemdUp extends HashistackEvent
case object ConsulDNSUp extends HashistackEvent
case object NomadDNSUp extends HashistackEvent
case object VaultDNSUp extends HashistackEvent

trait PlanTarget
case class CreateTask() extends PlanTarget
case class UpdateTask() extends PlanTarget
case class ReadTask() extends PlanTarget
case class DeleteTask() extends PlanTarget
case class NoopTask() extends PlanTarget
trait PlanStage
case class WaitingState() extends PlanStage
case class InflightState() extends PlanStage
case class IsDoneState() extends PlanStage

trait DNSStage { val name: String }
case class NoDNS(name: String) extends DNSStage
case class WaitForDNS(name: String) extends DNSStage
case class ResolveDNS(name: String) extends DNSStage
case class ErrorDNS(name: String) extends DNSStage
case class EmptyDNS(name: String) extends DNSStage // When does this happen?

object TerraformMagic {
  case class TerraformPlan(
    create: Set[String],
    read: Set[String],
    update: Set[String],
    delete: Set[String],
    noop: Set[String]
  ) {
    def removecreate(elem: String): TerraformPlan = this.copy(create = this.create.filterNot(_ == elem))
    def removeread(elem: String): TerraformPlan = this.copy(read = this.read.filterNot(_ == elem))
    def removeupdate(elem: String): TerraformPlan = this.copy(update = this.update.filterNot(_ == elem))
    def removedelete(elem: String): TerraformPlan = this.copy(delete = this.delete.filterNot(_ == elem))
    def removenoop(elem: String): TerraformPlan = this.copy(noop = this.noop.filterNot(_ == elem))

    def addcreate(elem: String): TerraformPlan = this.copy(create = this.create + elem)
    def addread(elem: String): TerraformPlan = this.copy(read = this.read + elem)
    def addupdate(elem: String): TerraformPlan = this.copy(update = this.update + elem)
    def adddelete(elem: String): TerraformPlan = this.copy(delete = this.delete + elem)
    def addnoop(elem: String): TerraformPlan = this.copy(noop = this.noop + elem)

    def diff(other: TerraformPlan): TerraformPlan =
      TerraformPlan(
        create = _symdiff(this.create, other.create),
        read = _symdiff(this.read, other.read),
        update = _symdiff(this.update, other.update),
        delete = _symdiff(this.delete, other.delete),
        noop = _symdiff(this.noop, other.noop)
      )
    def all(): Set[String] = {
      this.create.union(this.read).union(this.update).union(this.delete).union(this.noop)
    }
    def filter(allowed: Set[String]): TerraformPlan = {
      TerraformPlan(
        create = this.create.intersect(allowed),
        read = this.read.intersect(allowed),
        update = this.update.intersect(allowed),
        delete = this.delete.intersect(allowed),
        noop = this.noop.intersect(allowed)
      )
    }

    def _symdiff[A](a: Set[A], b: Set[A]): Set[A] = (a | b) -- (a & b)
  }
  case object TerraformPlan {
    def empty: TerraformPlan = TerraformPlan(Set(), Set(), Set(), Set(), Set())
  }

  sealed trait TFStateChange
  case class DestroyStart(name: String, id: String) extends TFStateChange
  case class DestroyComplete(name: String, duration: String) extends TFStateChange
  case class Data(name: String) extends TFStateChange
  case class CreateStart(name: String) extends TFStateChange
  case class CreateComplete(name: String, duration: String, id: String) extends TFStateChange
  case class ApplyComplete() extends TFStateChange
  case class ReadStart(name: String) extends TFStateChange
  case class ReadComplete(name: String, duration: String) extends TFStateChange
  case class NotChange() extends TFStateChange

  val destroyPat = "(.*): Destroying... (.*)".r.unanchored
  val destroyCompletePat = "(.*): Destruction complete after (.*)".r.unanchored
  val destroyRefreshingStatePat = "(.*): Refreshing state... (.*)".r.unanchored //Match this one first
  val creationCompletePat = "(.*): Creation complete after (.*) (.*)".r.unanchored
  val createRefreshingStatePat = "(.*): Refreshing state...".r.unanchored // before this one
  val creatingPat = "(.*): Creating...".r.unanchored
  val readingPat = "(.*): Reading...".r.unanchored
  val stillReadingPat = "(.*): Still reading... (.*)".r.unanchored
  val readCompletePat = "(.*): Read complete after (.*)".r.unanchored
  val applyCompletePat = "Apply complete! Resources: (\\d+) added, (\\d+) changed, (\\d+) destroyed.".r.unanchored

  val getnamePat = ".*\\.(.*)\\[0\\]".r.unanchored
  val namePat = "(\\w+)".r.unanchored
  val nomadJobPat = "nomadjob\\.(.*)".r.unanchored
  val consulHPat = "consulservicehealth\\.(.*)".r.unanchored
  def resolveName(in: String): String = cleanStr(in) match {
    case getnamePat(name)  => name
    case nomadJobPat(name) => name
    case consulHPat(name)  => name
    case namePat(name)     => name
    case _ => {
      LogTUI.writeLog(s"Parse error: $in?")
      in
    }
  }
  def cleanStr(str: String): String = {
    str.replaceAll("[^A-Za-z0-9.,!:\\[\\] ]", "")
  }
  def translate(in: String): TerraformMagic.TFStateChange = {
    cleanStr(in) match {
      case TerraformMagic.destroyPat(name, id) => TerraformMagic.DestroyStart(resolveName(name), id)
      case TerraformMagic.destroyCompletePat(name, duration) => TerraformMagic.DestroyComplete(resolveName(name), duration)
      case TerraformMagic.destroyRefreshingStatePat(name, id) => TerraformMagic.Data(resolveName(name))
      case TerraformMagic.creatingPat(name)                   => TerraformMagic.CreateStart(resolveName(name))
      case TerraformMagic.creationCompletePat(name, duration, id) => TerraformMagic.CreateComplete(resolveName(name), duration, id)
      case TerraformMagic.createRefreshingStatePat(name)  => TerraformMagic.Data(resolveName(name))
      case TerraformMagic.readCompletePat(name, duration) => TerraformMagic.ReadComplete(resolveName(name), duration)
      case TerraformMagic.stillReadingPat(name)           => TerraformMagic.NotChange()
      case TerraformMagic.readingPat(name)                => TerraformMagic.ReadStart(resolveName(name))
      case TerraformMagic.applyCompletePat(n_added, n_changed, n_destroyed) =>
        TerraformMagic.ApplyComplete()
      case x => {
        LogTUI.writeLog(s"Parse err: $in")
        TerraformMagic.NotChange()
      }
    }
  }

  def stripindex(in: String): String = in.replaceAll("\\[[0-9]+\\]", "")

}

object CLIMagic {
  def ansi = new Ansi()

  val detectTmux: Boolean = {
    sys.env.get("TMUX").nonEmpty
  }

  val pathedTput = if (new java.io.File("/usr/bin/tput").exists()) "/usr/bin/tput" else "tput"
  def consoleDim(s: String) = {
    import sys.process._
    Seq("sh", "-c", s"$pathedTput $s 2> /dev/tty").!!.trim.toInt
  }
  def consoleRows: Int = {
    try consoleDim("lines")
    catch { case e: Throwable => 50 } // Accessing /dev/tty fails in CI, so arbitrary row count
  }

  def _print(text: Ansi): IO[Unit] = IO(System.out.print(text))
//  def _print(text: Ansi): IO[Unit] = IO()

  // For some reason ".render" moves down a line but not back to the leftmost column???
  def print(text: String): IO[Unit] = _print(ansi.render(text + "\n").cursorUpLine().cursorToColumn(0))

  def setupCLI(): IO[Unit] = {
    AnsiConsole.systemInstall()
    IO(Unit)
  }
  def shutdownCLI(): IO[Unit] = {
    AnsiConsole.systemUninstall()
    IO(Unit)
  }

  def clearScreenSpace(): IO[Unit] = {
    IO(System.out.print("\n" * consoleRows))
//    _print(ansi.cursorUp(consoleRows))
    // this might cause a blank screen at endTUI
  }
  def savePos(): IO[Unit] = {
    _print(ansi.saveCursorPosition())
  }
  def loadPos(): IO[Unit] = {
    _print(ansi.restoreCursorPosition())
    // or it's this being called by iterStateAndDraw
  }

  def move(count: Int): IO[Unit] = _print(count match {
     case x if x < 0 => ansi.cursorUp(x * -1)
     case x if x > 0 => ansi.cursorDown(x)
     case _ => ansi
  })

  def sweep(clean: Boolean = false): IO[Unit] = {
    (if (clean) _print(ansi.eraseScreen()) else IO(Unit)) *> loadPos() *> move(-1 * consoleRows)
  }
}

object LogTUI {
//  def debugprint(s: String): Unit = println(s)
  def debugprint(s: String): Unit = Unit

  var isActive = false
  var printerFiber: Option[Fiber[IO, Unit]] = None

  val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))
  implicit val timer = IO.timer(ec)

  implicit val cs = IO.contextShift(ec)

  private val extevtq = mutable.Queue.empty[HashistackEvent]
  private val initq = mutable.Queue.empty[String]
  private val applyq = mutable.Queue.empty[TerraformMagic.TFStateChange]
  private val dnsq = mutable.Queue.empty[DNSStage]
  private val planvar: MVar[IO, (TerraformMagic.TerraformPlan, Map[String, List[String]])] =
    MVar.empty[IO, (TerraformMagic.TerraformPlan, Map[String, List[String]])].unsafeRunSync()
  private val denouement = mutable.Queue.empty[String]

  final class MLock(mvar: MVar2[IO, Unit]) {
    def acquire(): IO[Unit] =
      mvar.take

    def release(): IO[Unit] =
      mvar.put(())
  }

  object MLock {
    def apply(): IO[MLock] =
      MVar[IO].of(()).map(ref => new MLock(ref))
  }

  val writing: MLock = MLock().unsafeRunSync()

  val log_path = RadPath.runtime / "timberland.log"

  /** *
   * Turns on the LogTUI display.
   *
   * NOTE - While the LogTUI is active, all terminal output should be through LogTUI.printAfter()
   * so that text-overwriting or lack-of-overwriting doesn't produce confusing displays
   *
   * */
  def startTUI(consulExists: Boolean = false, nomadExists: Boolean = false, vaultExists: Boolean = false): IO[Unit] = {
    val serviceStateAtStart = HashistackStates(
      consul = { if (consulExists) AlreadyResponding else Missing },
      nomad = { if (nomadExists) AlreadyResponding else Missing },
      vault = { if (vaultExists) AlreadyResponding else Missing }
    )

    if (isActive) IO.unit
    else if (CLIMagic.detectTmux) {
      println("It seems we are running in tmux ($TMUX is set); not displaying TUI")
      // this doesn't seem to work
      IO.unit
    } else
      for {
        _ <- IO {
          isActive = true
        }
        _ <- IO.shift(cs)
        printer <- Printer.beginIterPrint(serviceStateAtStart).start
        _ <- IO(this.printerFiber = Some(printer))
      } yield ()
  }

  /*
   * Cancels LogTUI fiber and prints out all messages stored in calls to LogTUI.printAfter()
   * */
  def endTUI(error: Option[Throwable] = None): IO[Unit] =
    for {
      wasActive <- IO(isActive)
      _ <- if (wasActive) IO(writeLog("shutting down TUI")) else IO.unit
      _ <- IO { isActive = false }
      _ <- if (error.isEmpty) IO.sleep(2.seconds) else IO.unit
      _ <- IO {
        if (wasActive) {
          printerFiber.get.cancel
          Console.flush()
          denouement.foreach(println)
          println()
          error match {
            case None => println("Complete")
            case Some(definitely_error) => {
              println("Encountered Errors")
              println("Startup hit exception:")
              val sw = new StringWriter
              definitely_error.printStackTrace(new PrintWriter(sw))
              println(sw.toString)
              sys.exit(1)
            }
          }
          println("\n")
        } else denouement.foreach(println)
      }
    } yield ()

  def writeLog(output: String): Unit = {
    if (!isActive) println(output)
    os.write.append(log_path, output + "\n")
    debugprint(output)
  }

  def writeLogFromStream(bytearr: Array[Byte], len: Int): Unit = {
    val str = new String(bytearr.take(len), StandardCharsets.UTF_8)
    writeLog(str)
  }

  /**
   * Receives initial plan info from calls to terraform plan/show
   * */
  def plan(planDeps: (TerraformMagic.TerraformPlan, Map[String, List[String]])): IO[Unit] = {
    debugprint(s"plan " + planDeps._2.toString())
    IO(Unit)

    planvar.put(planDeps)
  }

  /**
   * Receives state updates from waitForDNS to track DNS access to services/modules
   * */
  def dns(stg: DNSStage): IO[Unit] = {
    writeLog(s"DNS: $stg")

    stg match {
      case WaitForDNS("nomad.service.consul") => Unit
      case ResolveDNS("nomad.service.consul") => extevtq.enqueue(NomadDNSUp) // Hacky!
      case WaitForDNS("vault.service.consul") => Unit
      case ResolveDNS("vault.service.consul") => extevtq.enqueue(VaultDNSUp)
      case other => {
        dnsq.enqueue(other)
      }
    }
    IO(Unit)
  }

  /**
   * Receives state updates from startup functions for systemd services (Consul, Nomad, Vault)
   * */
  def event(evt: HashistackEvent): Unit = {
    writeLog(s"Services: $evt")
    debugprint("extevtq + " + evt.toString())
    extevtq.enqueue(evt)
  }

  /**
   * Receives data from terraform init
   * */
  def init(bytearr: Array[Byte], len: Int): Unit = {
    val str = new String(bytearr.take(len), StandardCharsets.UTF_8)
    initq.enqueue(str.split("\n"): _*)
    writeLog(str)
    debugprint(s"init ${initq.size} $str")
  }

  /**
   * Receives output from Terraform apply process
   * */
  def tfApply(bytearr: Array[Byte], len: Int): Unit = {
    val str = new String(bytearr.take(len), StandardCharsets.UTF_8)
    val steps = str.split("\n").filterNot(_ == "").map(TerraformMagic.translate)
    applyq.enqueue(steps: _*)
    debugprint(s"tfapply ${applyq.size} ${steps} $str")
  }

  def vault(bytearr: Array[Byte], len: Int): Unit = writeLogFromStream(bytearr, len)

  def stdErrs(source: String)(bytearr: Array[Byte], len: Int): Unit = {
    val str = new String(bytearr.take(len), StandardCharsets.UTF_8)
    printAfter(s"stderr from $source:\t$str")
    writeLog(s"stderr from $source:\t$str")
  }

  /**
   * Collects print statements during LogTUI execution to be shown after the
   * TUI display is terminated.  Use this for things like generated tokens
   * or status output that the user will need to have post-startup.
   *
   * */
  def printAfter(str: String): Unit = {
    denouement.enqueue(str)
    writeLog(s"${str}")
  }

  object Printer {
    case class Apply(pending: TerraformMagic.TerraformPlan, deps: Map[String, List[String]])

    sealed trait ServiceState
    case object Systemdnotstarted extends ServiceState
    case object Dnsnotresolving extends ServiceState
    case object Done extends ServiceState
    case object Missing extends ServiceState
    case object AlreadyResponding extends ServiceState
    case class HashistackStates(
                         consul: ServiceState = Missing,
                         nomad: ServiceState = Missing,
                         vault: ServiceState = Missing
    ) {
      def updateHashistackStates(in: HashistackEvent): HashistackStates =
        in match {
          case ConsulStarting =>
            this.copy(consul = Systemdnotstarted)
          case NomadStarting =>
            this.copy(nomad = Systemdnotstarted)
          case VaultStarting =>
            this.copy(vault = Systemdnotstarted)
          case ConsulSystemdUp =>
            this.copy(consul = Dnsnotresolving)
          case NomadSystemdUp =>
            this.copy(nomad = Dnsnotresolving)
          case VaultSystemdUp =>
            this.copy(vault = Dnsnotresolving)
          case ConsulDNSUp =>
            this.copy(consul = Done)
          case NomadDNSUp =>
            this.copy(nomad = Done)
          case VaultDNSUp =>
            this.copy(vault = Done)
        }
    }
    case object HashistackStates {
      def empty: HashistackStates = HashistackStates()
    }

    case class State(
          hashistackStates: Option[HashistackStates] = None,
          apply: Option[Apply] = None,
          inflight: Option[TerraformMagic.TerraformPlan] = None, // Changed since deps is immutable
          quorum: Map[String, DNSStage] = Map.empty,
          // these next three should be part of DrawingState
          linemap: Map[String, Int] = Map.empty,
          lineno: Int = 0,
          screenSize: Int = CLIMagic.consoleRows,
          tick: Int = 0
    ) { // Considering lineno as number of lines currently being printed
      // SL: inconsistent use of lineno vs lastline FML
      def modifyHashistackStates(modifier: HashistackStates => HashistackStates): State = this.copy(hashistackStates = this.hashistackStates.map(modifier))
      def modifyApply(modifier: TerraformMagic.TerraformPlan => TerraformMagic.TerraformPlan): State = {
        this.copy(apply = this.apply.map(ap => ap.copy(pending = modifier(ap.pending))))
      }
      def modifyInflight(modifier: TerraformMagic.TerraformPlan => TerraformMagic.TerraformPlan): State = {
        this.copy(inflight = this.inflight.map(modifier))
      }
      def modifyQuorum(name: String, value: DNSStage): State = {
        this.copy(quorum = quorum + (name -> value))
      }
    }
    case object State {
      def empty: State = State()
    }

    /**
     * Helper function for iterStateAndPrint; applies the name-extraction regex to all
     * module names received from "terraform plan"
     *
     * @param plan_tuple the TerraformPlan and dependency mapping as produced by
     *                   daemonutil.readTerraformPlan
     *
     * @return Apply object with names formatted to match those received via tfapply
     * */
    def translatePlan(plan_tuple: (TerraformPlan, Map[String, List[String]])): Apply = {
      val (plan, deps) = plan_tuple
      val translatedPlan = plan.copy(
        create = plan.create.map(TerraformMagic.resolveName),
        read = plan.read.map(TerraformMagic.resolveName),
        update = plan.update.map(TerraformMagic.resolveName),
        delete = plan.delete.map(TerraformMagic.resolveName),
        noop = plan.noop.map(TerraformMagic.resolveName)
      )
      val translated_deps = deps.keys
        .foldLeft(Map.empty[String, List[String]])((mp, k) =>
          mp + (TerraformMagic.resolveName(k) -> deps(k).map(TerraformMagic.resolveName))
        )

      Apply.tupled((translatedPlan, translated_deps))
    }

    /**
     * Checks for input from any of the new-state queues, dispatches
     * to the appropriate State-updating functions(s) for each non-empty queue,
     * and invokes draw.  Recursively calls itself.
     *
     * @param st represents the current known state of timberland
     *           and the display
     * @param plan represents the full Terraform plan for the current operation
     *
     * Does not return
     *
     * */
    def iterStateAndPrint(st: State, plan: Option[Apply]): IO[Unit] = {
      for {
        externalEventData <- IO(extevtq.nonEmpty)
        initData <- IO(initq.nonEmpty)
        planData <- planvar.isEmpty.map(x => !x) // fake nonEmpty
        applyData <- IO(applyq.nonEmpty)
        dnsData <- IO(dnsq.nonEmpty)
        newPlan <- if (planData) planvar.take.map(translatePlan).map(x => Some(x)) else IO(plan)
        planState = if (planData) st.copy(apply = newPlan, inflight = Some(TerraformPlan.empty)) else st

        newState <- for {
          a <- if (externalEventData) iterateHashistackStates(planState) else IO(planState)
          b <- if (initData) init(a) else IO(a)
          c <- if (applyData && plan.nonEmpty) tfapplyp(plan)(b) else IO(b)
          d <- if (dnsData) quorum(c) else IO(c)
        } yield d
        drawnState <- draw(st, newState)
        _ <- IO(Console.flush())
        _ <- if (isActive) IO.sleep(500.millis) *> iterStateAndPrint(drawnState, newPlan) else CLIMagic.loadPos()
      } yield ()
    }

    /**
     * Updates state to reflect events from the systemd service startup
     * */
    def iterateHashistackStates(st: State): IO[State] =
      for {
        ext <- IO(extevtq.headOption.map(_ => extevtq.dequeueAll(_ => true).toList))
        newStates = ext match {
          case None        => st.hashistackStates
          case Some(value) =>
            // Apply all the log messages to determine new print state, in order
            value
              .map({
                in: HashistackEvent => {states: HashistackStates => states.updateHashistackStates(in)}
              })
              .foldLeft(st.hashistackStates)({
                case (Some(st), f) => Some(f(st))
                case (None, f)     => Some(f(HashistackStates.empty))
              })
        }
        newState <- IO.pure(st.copy(hashistackStates = newStates))
      } yield newState

    def init(st: State): IO[State] =
      for {
        evt <- IO(initq.headOption.map(_ => initq.dequeueAll(_ => true).toList))
        //_ <- IO(evt.map(debugprint))
      } yield st

    /**
     * Updates state to reflect events from waitForDNS
     * */
    def quorum(st: State): IO[State] = {
      def applyQuorumUpdates(st: State, change: DNSStage): State = {
        st.modifyQuorum(change.name, change)
      }

      for {
        evt <- IO(dnsq.headOption.map(_ => dnsq.dequeueAll(_ => true).toList))
        newstate = evt match {
          case None           => st
          case Some(dns_evts) => dns_evts.foldl(st)(applyQuorumUpdates)
        }
      } yield newstate
    }

    /**
     * Updates state to reflect a single event from Terraform.
     *
     * State keeps track of tasks in the Terraform plan as being waiting-to-occur or "in-flight" (a task
     * which is in the overall plan but is in neither of these categories is assumed complete.)  As Terraform events
     * (Create, Delete, etc against particular targets) are assimilated the lists are manipulated appropriately
     *
     * @param deps Map of each module/health-check to its dependencies; used to infer task completion in cases
     *             where these aren't directly reported
     * @param st State being updated
     * @param change the Terraform event being incorporated
     * @return updated State
     *
     * */
    def applyChange(deps: Map[String, List[String]])(st: State, change: TerraformMagic.TFStateChange): State = {
      change match {
        case TerraformMagic.DestroyStart(name, id) => {
          st.modifyApply(_.removedelete(name))
            .modifyInflight(_.adddelete(name)) // Take thing from app.destroy and add to inflight
        }
        case TerraformMagic.DestroyComplete(name, duration) => {
          st.modifyInflight(_.removedelete(name))
        }
        case TerraformMagic.Data(name) => {
          st.modifyApply(_.removeread(name)).modifyInflight(_.addread(name)) // Take thing from app.read
        }
        case TerraformMagic.CreateStart(name) => {
          st.modifyApply(_.removecreate(name))
            .modifyInflight(_.addcreate(name)) // Take thing from app.create and add to inflight.create
        } //check data dependency fulfillment + emit line
        case TerraformMagic.CreateComplete(name, duration, id) => {
          st.modifyInflight(_.removecreate(name)) // Take thing from inflight.create
        }
        case TerraformMagic.ReadStart(name) => {
          st.modifyApply(_.removeread(name)).modifyInflight(_.addread(name))
        }
        case TerraformMagic.ReadComplete(name, _) => {
          st.modifyInflight(_.removeread(name))
        }
        case TerraformMagic.ApplyComplete() => {
          // Clear app and inflight (assert that app is empty?)
          st.copy(apply = None, inflight = None)
        }
        case TerraformMagic.NotChange() => st
      }
    }

    // tfApplyChanges?
    def tfapplyp(maybeApply: Option[Apply])(st: State): IO[State] = {
      maybeApply match {
        case None => IO(st)
        case Some(app) =>
          for {
            evt <- IO(applyq.headOption.map(_ => applyq.dequeueAll(_ => true).toList))
            newstate <- IO(evt match {
              case None              => st
              case Some(change_list) => change_list.reverse.foldl(st)(applyChange(app.deps))
            })
          } yield newstate
      }
    }

    sealed case class DrawingState(st: State, currentLine: Int, lastLine: Int, screenSize: Int, screenOvershoot: Int) {
      def incrementLastline: DrawingState = {
        this.copy(lastLine = this.lastLine + 1)
      }
      def setCurrentLine(line: Int): DrawingState = {
        this.copy(currentLine = line)
      }
      def incrementOvershoot: DrawingState = {
        this.copy(screenOvershoot = this.screenOvershoot + 1)
      }
      def addLine(name: String, lineno: Int): DrawingState = {
        this.copy(
          st = st.copy(linemap = st.linemap + (name -> lineno)),
          lastLine = this.lastLine + 1
        )
      }
      def incrementTick: DrawingState = {
        this.copy(st = st.copy(tick = st.tick + 1))
      }
      def getState: State = {
        st.copy(lineno = lastLine)
      }
    }
    case object DrawingState {
      def apply(st: State): DrawingState = {
        DrawingState(st, 0, st.lineno, CLIMagic.consoleRows - 3, 0)
      }
    }

    /**
     * Updates a single line of the LogTUI display, moving the cursor appropriately
     * such that lines don't have to be updated in order
     *
     * @param dst tuple of (current cursor line, last line currently written to in the display,
     *          display state representation)
     * @param element tuple of (key identifying the line being updated, string to be written to the line)
     * @return tuple equivalent to clistate, with elements updated to reflect cursor movement and new lines
     *
     * */
    def updateElement(dst: DrawingState, element: (String, String)): IO[DrawingState] = {
      val (name, update) = element

      val addingNew = !dst.st.linemap.contains(name)
      val targetLine = if (addingNew) dst.lastLine else dst.st.linemap(name)

      val newDrawingState = if (targetLine <= (dst.screenSize - 2)) {
        val line = targetLine - dst.currentLine
        CLIMagic.move(line) *> CLIMagic.print(update) *> IO(dst.setCurrentLine(targetLine))
      } else {
        IO(dst.incrementOvershoot)
      }
      if (addingNew)
        newDrawingState.map(_.addLine(name, targetLine))
//          (target_line, last_line + 1, screen_size, st.copy(linemap = st.linemap + (name -> target_line)))
      else
        newDrawingState
//          (target_line, last_line, screen_size, st)
    }

    /**
     * Draws the little ticking ellipsis at the bottom of the screen;
     * does not draw if this is a non-interactive terminal (otherwise the whole
     * log winds up full of dots)
     * */
    def updateTicker(dst: DrawingState): IO[DrawingState] = {
      if (tty_mode) {
        val tickStage = dst.st.tick % 3
        for {
          _ <- CLIMagic.move(Math.min(dst.lastLine - dst.currentLine, (CLIMagic.consoleRows - 2) - dst.currentLine))
          _ <- CLIMagic.print(PrintElements.ticker(tickStage, dst.screenOvershoot))
          _ <- CLIMagic.move(1)
        } yield dst.incrementTick
      } else {
        IO(dst)
      }
    }

    /**
     * @param tf Partial Terraform plan representation containing all Terraform tasks that
     *           are currently at a given execution stage
     * @param stage Execution stage representation
     * @return List of (line id, line content) pairs (for eg update_element())
     *
     * */
    def terraformPlanStatements(tf: TerraformPlan, stage: PlanStage): List[(String, String)] = {
      (
        (for (c <- tf.create) yield (c, PrintElements.componentLine(c, CreateTask(), stage))) |
          (for (u <- tf.update) yield (u, PrintElements.componentLine(u, UpdateTask(), stage))) |
          (for (r <- tf.read) yield (r, PrintElements.componentLine(r, ReadTask(), stage))) |
          (for (d <- tf.delete) yield (d, PrintElements.componentLine(d, DeleteTask(), stage))) |
          (for (n <- tf.noop) yield (n, PrintElements.componentLine(n, NoopTask(), stage)))
      ).toList
    }

    /**
     * @param prestart contains execution stage info for systemd services (consul. nomad, vault)
     * @return (line id, line content) pairs for e.g. update_element()
     *
     * */
    def prestartStatements(prestart: HashistackStates): List[(String, String)] = {
      List(
        ("Consul", PrintElements.hashistackLine("Consul", prestart.consul)),
        ("Nomad", PrintElements.hashistackLine("Nomad", prestart.nomad)),
        ("Vault", PrintElements.hashistackLine("Vault", prestart.vault))
      )
    }

    /**
     * Renders DNS status lines
     * */
    def quorumStatement(stage: DNSStage): (String, String) = {
      (stage.name, PrintElements.quorumLine(stage))
    }

    /**
     * Initializes the LogTUI screen, writing the big ###### bars and the expected
     * systemd services
     *
     * */
    def initDraw(st: State, plan: Apply): IO[State] = {
      val display_seq =
        List(("1", PrintElements.displayBar), ("2", "")) ++
          prestartStatements(st.hashistackStates.getOrElse(HashistackStates())) ++
          List(("3", ""), ("4", PrintElements.displayBar), ("5", ""))

      val newDrawingState = display_seq.foldLeftM(DrawingState(st))(updateElement)
      CLIMagic.clearScreenSpace() *> CLIMagic.savePos() *> CLIMagic.sweep() *> newDrawingState.map(_.getState)
    }

    /**
     * Draws to the screen the diff between the current state and the previous state.
     *
     * @param oldState State representation from the previous iteration
     * @param st updated State representation
     * @return updated State which has been further updated with current display state
     *
     * */
    def draw(oldState: State, st: State): IO[State] = {
      // I think screenChanged checks for resizing the terminal, but I'm not sure
      val screenChanged = st.screenSize != CLIMagic.consoleRows
      val hashistackNeedsUpdate = (oldState.hashistackStates != st.hashistackStates) | screenChanged
      val applyNeedsUpdate = if (screenChanged) {
        st.apply.map(ifl => ifl.pending.all).getOrElse(Set.empty[String])
      } else {
        (oldState.apply, st.apply) match {
          case (None, None)                 => Set.empty[String]
          case (None, Some(app))            => app.pending.all
          case (Some(app), None)            => app.pending.all
          case (Some(oldapp), Some(newapp)) => newapp.pending.diff(oldapp.pending).all
        }
      }
      val inflightNeedsUpdate = if (screenChanged) {
        st.inflight.map(_.all).getOrElse(Set.empty[String])
      } else {
        (oldState.inflight, st.inflight) match {
          case (None, None)                 => Set.empty[String]
          case (None, Some(ifl))            => ifl.all
          case (Some(ifl), None)            => ifl.all
          case (Some(oldifl), Some(newifl)) => newifl.diff(oldifl).all
        }
      }
      val tfNeedsUpdate = applyNeedsUpdate.union(inflightNeedsUpdate)
      val quorumNeedsUpdate = if (screenChanged) {
        oldState.quorum.keySet | st.quorum.keySet
      } else {
        (oldState.quorum.keySet | st.quorum.keySet)
          .filter(k => oldState.quorum.getOrElse(k, NoDNS(k)) != st.quorum.getOrElse(k, NoDNS(k)))
      }

      val hashistackUpdateLines = if (hashistackNeedsUpdate) {
        prestartStatements(st.hashistackStates.getOrElse(HashistackStates.empty))
      } else {
        List.empty
      }
      val tfUpdateLines = if (tfNeedsUpdate.nonEmpty) {
        val updateApply =
          oldState.apply.map(apply => apply.pending.filter(tfNeedsUpdate)).getOrElse(TerraformPlan.empty)
        val updateInflight = oldState.inflight.getOrElse(TerraformPlan.empty).filter(tfNeedsUpdate)
        (
          terraformPlanStatements(updateApply, InflightState()) ++
            terraformPlanStatements(updateInflight, IsDoneState())
        )
      } else {
        List.empty
      }
      val quorumUpdateLines = if (quorumNeedsUpdate.nonEmpty) {
        quorumNeedsUpdate.map(k => quorumStatement(st.quorum.getOrElse(k, NoDNS(k))))
      } else {
        List.empty
      }

      val extraUpdateLines = if (screenChanged) {
        List(
          ("1", PrintElements.displayBar),
          ("2", PrintElements.blankLine),
          ("3", PrintElements.blankLine),
          ("4", PrintElements.displayBar),
          ("5", PrintElements.blankLine)
        )
      } else {
        List.empty[(String, String)]
      }

      val updateLines = hashistackUpdateLines ++ tfUpdateLines ++ quorumUpdateLines ++ extraUpdateLines

      val newDrawingState = updateLines.foldLeftM(DrawingState(st))(((a, b) => updateElement(a, b)))

      LogTUI.writing.acquire *>
        CLIMagic.sweep(screenChanged) *>
        newDrawingState
          .flatMap(updateTicker)
          .map(_.getState)
          .flatMap(st =>
            CLIMagic
              .sweep()
              *> CLIMagic
                .move(st.lineno + 1) // stay below the last line when not drawing
                .map(_ => if (screenChanged) st.copy(screenSize = CLIMagic.consoleRows) else st)
              <* LogTUI.writing.release
          )

    }

    def beginIterPrint(prestate: HashistackStates): IO[Unit] = {
      val initialState = State.empty.copy(hashistackStates = Some(prestate))

      for {
        _ <- CLIMagic.setupCLI()
        st <- initDraw(initialState, Apply(TerraformPlan.empty, Map.empty))
        _ <- iterStateAndPrint(st, None)
      } yield ()
    }

    def tty_mode: Boolean = {
      isatty(STDOUT_FILENO) == 1
    }
  }
}

object PrintElements {
  def displayBar: String = {
    "########################################################              "
  }
  def blankLine: String = {
    "                                                                      "
  }
  def hashistackLine(name: String, state: ServiceState): String = {
    state match {
      case LogTUI.Printer.Systemdnotstarted => s"$name:\t@|bold Waiting for systemd start|@"
      case LogTUI.Printer.Dnsnotresolving   => s"$name:\t@|green Service started|@           "
      case LogTUI.Printer.Done              => s"$name:\t@|green Service started and DNS resolves|@     "
      case LogTUI.Printer.Missing           => s"$name:\t@|faint ...|@                                 "
      case LogTUI.Printer.AlreadyResponding => s"$name:\t@|green Is active|@" // TODO asserts too much?
    }
  }

  def componentLine(rawName: String, target: PlanTarget, stage: PlanStage): String = {
    val name = TerraformMagic.resolveName(rawName)
    val verb = target match {
      case CreateTask() => "Create"
      case UpdateTask() => "Update"
      case ReadTask()   => "Receive"
      case DeleteTask() => "Delete"
      case NoopTask()   => "Do nothing"
    }
    stage match {
      case WaitingState()  => s"@|bold ${verb} $name|@  ".padTo(50, '-') + " @|faint Waiting|@                 "
      case InflightState() => s"@|bold ${verb} $name|@  ".padTo(50, '-') + " @|bold In process...|@           "
      case IsDoneState() =>
        s"@|bold $verb $name|@  ".padTo(50, '-') + " @|green Successful|@                             "
    }
  }

  def quorumLine(stage: DNSStage): String = {
    stage match {
//      case NoDNS(name) => s"@|faint Not waiting for DNS for $name|@                      "
      case WaitForDNS(name) => s"$name:".padTo(70, '-') + " @|bold Waiting for DNS resolution|@              "
      case ResolveDNS(name) => s"$name:".padTo(70, '-') + " @|green DNS resolved successfully|@              "
      case ErrorDNS(name)   => s"$name:".padTo(70, '-') + " @|yellow DNS lookup encountered errors|@           "
      case EmptyDNS(name)   => s"$name:".padTo(70, '-') + " @|yellow DNS lookup resolved with empty results|@  "
    }
  }

  def ticker(stage: Int, more: Int): String = {
    val ellipses = "." * (stage + 1) + blankLine
    if (more > 0) {
      s"... ($more more) " + ellipses
    } else {
      ellipses
    }
  }
}
