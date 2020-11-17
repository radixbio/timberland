package com.radix.timberland.util

import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

import cats.effect.{IO, _}
import cats.effect.concurrent.MVar
import cats.implicits._
import com.radix.timberland.util.LogTUI.Printer.{AlreadyResponding, Missing, Prestart, PrestartState}
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

sealed trait WKEvent
//TODO up/down
case object ConsulStarting extends WKEvent
case object NomadStarting extends WKEvent
case object VaultStarting extends WKEvent
case object ConsulSystemdUp extends WKEvent
case object NomadSystemdUp extends WKEvent
case object VaultSystemdUp extends WKEvent
case object ConsulDNSUp extends WKEvent
case object NomadDNSUp extends WKEvent
case object VaultDNSUp extends WKEvent

trait PlanTarget
case class CreateT() extends PlanTarget
case class UpdateT() extends PlanTarget
case class ReadT() extends PlanTarget
case class DeleteT() extends PlanTarget
case class NoopT() extends PlanTarget
trait PlanStage
case class WaitingS() extends PlanStage
case class InflightS() extends PlanStage
case class IsDoneS() extends PlanStage

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
  def TFStateChangePriority(sc: TFStateChange): Int =
    sc match {
      case NotChange()             => 0
      case DestroyStart(_, _)      => 1
      case CreateStart(_)          => 2
      case Data(_)                 => 3
      case DestroyComplete(_, _)   => 4
      case CreateComplete(_, _, _) => 5
      case ApplyComplete()         => 6
    }

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

  def cleanStr(str: String): String = {
    str.replaceAll("[^A-Za-z0-9.,!:\\[\\] ]", "")
  }
  val getnamePat = ".*\\.(.*)\\[0\\]".r.unanchored
  val namePat = "(\\w+)".r.unanchored
  val nomadJobPat = "nomadjob\\.(.*)".r.unanchored
  val consulHPat = "consulservicehealth\\.(.*)".r.unanchored
  def res_name(in: String): String = cleanStr(in) match {
    case getnamePat(name)  => name
    case nomadJobPat(name) => name
    case consulHPat(name)  => name
    case namePat(name)     => name
    case _ => {
      LogTUI.writeLog(s"Parse error: $in?")
      in
    }
  }
  def translate(in: String): TerraformMagic.TFStateChange = {
    cleanStr(in) match {
      case TerraformMagic.destroyPat(name, id)                => TerraformMagic.DestroyStart(res_name(name), id)
      case TerraformMagic.destroyCompletePat(name, duration)  => TerraformMagic.DestroyComplete(res_name(name), duration)
      case TerraformMagic.destroyRefreshingStatePat(name, id) => TerraformMagic.Data(res_name(name))
      case TerraformMagic.creatingPat(name)                   => TerraformMagic.CreateStart(res_name(name))
      case TerraformMagic.creationCompletePat(name, duration, id) =>
        TerraformMagic.CreateComplete(res_name(name), duration, id)
      case TerraformMagic.createRefreshingStatePat(name)  => TerraformMagic.Data(res_name(name))
      case TerraformMagic.readCompletePat(name, duration) => TerraformMagic.ReadComplete(res_name(name), duration)
      case TerraformMagic.stillReadingPat(name)           => TerraformMagic.NotChange()
      case TerraformMagic.readingPat(name)                => TerraformMagic.ReadStart(res_name(name))
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
  }
  def savePos(): IO[Unit] = {
    _print(ansi.saveCursorPosition())
  }
  def loadPos(): IO[Unit] = {
    _print(ansi.restoreCursorPosition())
  }

  def move(count: Int): IO[Unit] = {
    if (count > 0) {
      _print(ansi.cursorDown(count))
    } else if (count < 0) {
      _print(ansi.cursorUp(count * -1))
    } else _print(ansi)
  }

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

  private val extevtq = mutable.Queue.empty[WKEvent]
  private val initq = mutable.Queue.empty[String]
  private val applyq = mutable.Queue.empty[TerraformMagic.TFStateChange]
  private val dnsq = mutable.Queue.empty[DNSStage]
  private val planvar: MVar[IO, (TerraformMagic.TerraformPlan, Map[String, List[String]])] =
    MVar.empty[IO, (TerraformMagic.TerraformPlan, Map[String, List[String]])].unsafeRunSync()
  private val denouement = mutable.Queue.empty[String]

  val log_path = RadPath.runtime / "timberland.log"

  /** *
   * Turns on the LogTUI display.
   *
   * NOTE - While the LogTUI is active, all terminal output should be through LogTUI.printAfter()
   * so that text-overwriting or lack-of-overwriting doesn't produce confusing displays
   *
   * */
  def startTUI(consulExists: Boolean = false, nomadExists: Boolean = false, vaultExists: Boolean = false): IO[Unit] = {
    val serviceStateAtStart = Prestart(
      consul = { if (consulExists) AlreadyResponding else Missing },
      nomad = { if (nomadExists) AlreadyResponding else Missing },
      vault = { if (vaultExists) AlreadyResponding else Missing }
    )

    if (isActive) IO.unit
    else if (CLIMagic.detectTmux) {
      println("It seems we are running in tmux ($TMUX is set); not displaying TUI")
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
          if (error.isEmpty) {
            println("Complete")
          } else {
            println("Encountered Errors")
            println("Startup hit exception:")
            println(error.get.fillInStackTrace)
            sys.exit(1)
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
  def plan(plan_deps: (TerraformMagic.TerraformPlan, Map[String, List[String]])): IO[Unit] = {
    debugprint(s"plan " + plan_deps._2.toString())
    IO(Unit)

    planvar.put(plan_deps)
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
  def event(evt: WKEvent): Unit = {
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
  def tfapply(bytearr: Array[Byte], len: Int): Unit = {
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
    def getConsoleSize = {
      val pathedTput = if (new java.io.File("/usr/bin/tput").exists()) "/usr/bin/tput" else "tput"
      def consoleDim(s: String) = {
        import sys.process._
        Seq("sh", "-c", s"$pathedTput $s 2> /dev/tty").!!.trim.toInt
      }
      (consoleDim("cols"), consoleDim("lines"))
    }
    case class Apply(pending: TerraformMagic.TerraformPlan, deps: Map[String, List[String]])

    sealed trait PrestartState
    case object Systemdnotstarted extends PrestartState
    case object Dnsnotresolving extends PrestartState
    case object Done extends PrestartState
    case object Missing extends PrestartState
    case object AlreadyResponding extends PrestartState
    case class Prestart(
      consul: PrestartState = Missing,
      nomad: PrestartState = Missing,
      vault: PrestartState = Missing
    ) {
      def updatewk(in: WKEvent): Prestart =
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
    case object Prestart {
      def empty: Prestart = Prestart()
    }

    case class State(
      prestart: Option[Prestart] = None,
      app: Option[Apply] = None,
      inflight: Option[TerraformMagic.TerraformPlan] = None, // Changed since deps is immutable
      quorum: Map[String, DNSStage] = Map.empty,
      linemap: Map[String, Int] = Map.empty,
      lineno: Int = 0,
      screenSize: Int = CLIMagic.consoleRows,
      tick: Int = 0
    ) { // Considering lineno as number of lines currently being printed
      def mod_prestart(modifier: Prestart => Prestart): State = this.copy(prestart = this.prestart.map(modifier))
      def mod_app(modifier: TerraformMagic.TerraformPlan => TerraformMagic.TerraformPlan): State = {
        this.copy(app = this.app.map(ap => ap.copy(pending = modifier(ap.pending))))
      }
      def mod_inflight(modifier: TerraformMagic.TerraformPlan => TerraformMagic.TerraformPlan): State = {
        this.copy(inflight = this.inflight.map(modifier))
      }
      def mod_quorum(name: String, value: DNSStage): State = {
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
      val (tp, deps) = plan_tuple
      val translated_tp = tp.copy(
        create = tp.create.map(TerraformMagic.res_name),
        read = tp.read.map(TerraformMagic.res_name),
        update = tp.update.map(TerraformMagic.res_name),
        delete = tp.delete.map(TerraformMagic.res_name),
        noop = tp.noop.map(TerraformMagic.res_name)
      )
      val translated_deps = deps.keys
        .foldLeft(Map.empty[String, List[String]])((mp, k) =>
          mp + (TerraformMagic.res_name(k) -> deps(k).map(TerraformMagic.res_name))
        )

      Apply.tupled((translated_tp, translated_deps))
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
        evtextdata <- IO(extevtq.nonEmpty)
        initdata <- IO(initq.nonEmpty)
        plandata <- planvar.isEmpty.map(x => !x) // fake nonEmpty
        appdata <- IO(applyq.nonEmpty)
        dnsdata <- IO(dnsq.nonEmpty)
        newplan <- if (plandata) planvar.take.map(translatePlan).map(x => Some(x)) else IO(plan)
        planstate = if (plandata) st.copy(app = newplan, inflight = Some(TerraformPlan.empty)) else st

        newst <- for {
          a <- if (evtextdata) prestart(planstate) else IO(planstate)
          b <- if (initdata) init(a) else IO(a)
          c <- if (appdata && plan.nonEmpty) tfapplyp(plan)(b) else IO(b)
          d <- if (dnsdata) quorum(c) else IO(c)
        } yield d
        drawnst <- draw(st, newst)
        _ <- IO(Console.flush())

        _ <- if (isActive) IO.sleep(500.millis) *> iterStateAndPrint(drawnst, newplan) else CLIMagic.loadPos()
      } yield ()
    }

    /**
     * Updates state to reflect events from the systemd service startup
     * */
    def prestart(st: State): IO[State] =
      for {
        ext <- IO(extevtq.headOption.map(_ => extevtq.dequeueAll(_ => true).toList))
        newps = ext match {
          case None        => st.prestart
          case Some(value) =>
            // Apply all the log messages to deterimine new print state, in order
            value
              .map({
                in: WKEvent =>
                  { pst: Prestart =>
                    pst.updatewk(in)
                  }
              })
              .foldLeft(st.prestart)({
                case (Some(st), f) => Some(f(st))
                case (None, f)     => Some(f(Prestart.empty))
              })
        }
        newst <- IO.pure(st.copy(prestart = newps))
      } yield newst

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
        st.mod_quorum(change.name, change)
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
     * (Create, Delete, etc against particular targets) are assimilated the lists are manipulated appropirately
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
          st.mod_app(_.removedelete(name))
            .mod_inflight(_.adddelete(name)) // Take thing from app.destroy and add to inflight
        }
        case TerraformMagic.DestroyComplete(name, duration) => {
          st.mod_inflight(_.removedelete(name))
        }
        case TerraformMagic.Data(name) => {
          st.mod_app(_.removeread(name)).mod_inflight(_.addread(name)) // Take thing from app.read
        }
        case TerraformMagic.CreateStart(name) => {
          st.mod_app(_.removecreate(name))
            .mod_inflight(_.addcreate(name)) // Take thing from app.create and add to inflight.create
        } //check data dependency fulfillment + emit line
        case TerraformMagic.CreateComplete(name, duration, id) => {
          st.mod_inflight(_.removecreate(name)) // Take thing from inflight.create
        }
        case TerraformMagic.ReadStart(name) => {
          st.mod_app(_.removeread(name)).mod_inflight(_.addread(name))
        }
        case TerraformMagic.ReadComplete(name, _) => {
          st.mod_inflight(_.removeread(name))
        }
        case TerraformMagic.ApplyComplete() => {
          // Clear app and inflight (assert that app is empty?)
          st.copy(app = None, inflight = None)
        }
        case TerraformMagic.NotChange() => st
      }
    }

    def tfapplyp(opapp: Option[Apply])(st: State): IO[State] = {
      opapp match {
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

    sealed case class DrawingState(st: State, cur_line: Int, last_line: Int, screen_size: Int, screen_overshoot: Int) {
      def inc_lastline: DrawingState = {
        this.copy(last_line = this.last_line + 1)
      }
      def set_curline(line: Int): DrawingState = {
        this.copy(cur_line = line)
      }
      def inc_overshoot: DrawingState = {
        this.copy(screen_overshoot = this.screen_overshoot + 1)
      }
      def add_line(name: String, lineno: Int): DrawingState = {
        this.copy(
          st = st.copy(linemap = st.linemap + (name -> lineno)),
          last_line = this.last_line + 1
        )
      }
      def inc_tick: DrawingState = {
        this.copy(st = st.copy(tick = st.tick + 1))
      }
      def recoverState: State = {
        st.copy(lineno = last_line)
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
    def update_element(dst: DrawingState, element: (String, String)): IO[DrawingState] = {
      val (name, update) = element

      val adding_new = !dst.st.linemap.contains(name)
      val target_line = if (adding_new) dst.last_line else dst.st.linemap(name)

      val draw_op = if (target_line <= (dst.screen_size - 2)) {
        val line = target_line - dst.cur_line
        CLIMagic.move(line) *> CLIMagic.print(update) *> IO(dst.set_curline(target_line))
      } else {
        IO(dst.inc_overshoot)
      }

      if (adding_new)
        draw_op.map(_.add_line(name, target_line))
//          (target_line, last_line + 1, screen_size, st.copy(linemap = st.linemap + (name -> target_line)))
      else
        draw_op
//          (target_line, last_line, screen_size, st)
    }

    /**
     * Draws the little ticking ellipsis at the bottom of the screen;
     * does not draw if this is a non-interactive terminal (otherwise the whole
     * log winds up full of dots)
     * */
    def update_ticker(dst: DrawingState): IO[DrawingState] = {
      if (tty_mode) {
        val tick_stage = dst.st.tick % 3
        for {
          _ <- CLIMagic.move(Math.min(dst.last_line - dst.cur_line, (CLIMagic.consoleRows - 2) - dst.cur_line))
          _ <- CLIMagic.print(PrintElements.ticker(tick_stage, dst.screen_overshoot))
          _ <- CLIMagic.move(1)
        } yield dst.inc_tick
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
        (for (c <- tf.create) yield (c, PrintElements.componentLine(c, CreateT(), stage))) |
          (for (u <- tf.update) yield (u, PrintElements.componentLine(u, UpdateT(), stage))) |
          (for (r <- tf.read) yield (r, PrintElements.componentLine(r, ReadT(), stage))) |
          (for (d <- tf.delete) yield (d, PrintElements.componentLine(d, DeleteT(), stage))) |
          (for (n <- tf.noop) yield (n, PrintElements.componentLine(n, NoopT(), stage)))
      ).toList
    }

    /**
     * @param prestart contains execution stage info for systemd services (consul. nomad, vault)
     * @return (line id, line content) pairs for e.g. update_element()
     *
     * */
    def prestartStatements(prestart: Prestart): List[(String, String)] = {
      List(
        ("Consul", PrintElements.prestartLine("Consul", prestart.consul)),
        ("Nomad", PrintElements.prestartLine("Nomad", prestart.nomad)),
        ("Vault", PrintElements.prestartLine("Vault", prestart.vault))
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
    def init_draw(st: State, plan: Apply): IO[State] = {
      val display_seq =
        List(("1", PrintElements.displayBar), ("2", "")) ++
          prestartStatements(st.prestart.getOrElse(Prestart())) ++
          List(("3", ""), ("4", PrintElements.displayBar), ("5", ""))

      val print_update = display_seq.foldLeftM(DrawingState(st))(update_element)
      CLIMagic.clearScreenSpace() *> CLIMagic.savePos() *> CLIMagic.sweep() *> print_update.map(_.recoverState)
    }

    /**
     * Draws to the screen the diff between the current state and the previous state.
     *
     * @param oldst State representation from the previous iteration
     * @param st updated State representation
     * @return updated State which has been further updated with current display state
     *
     * */
    def draw(oldst: State, st: State): IO[State] = {
      val screenChanged = st.screenSize != CLIMagic.consoleRows
//      val update_lines : List[(String, String)] = if (screenChanged) {
//        prestartStatements(st.prestart.getOrElse(Prestart.empty)) ++
//        st.app.map(ifl => ifl.pending.all.toList).getOrElse(List.empty[(String, String)]) ++
//        st.inflight.map(_.all.toList).getOrElse(List.empty[(String, String)]) ++
//        List(("1", PrintElements.displayBar), ("2", ""),
//          ("3", ""), ("4", PrintElements.displayBar), ("5", ""))
//      } else {
      val pres_needs_update = (oldst.prestart != st.prestart) | screenChanged
      val app_needs_update = if (screenChanged) {
        st.app.map(ifl => ifl.pending.all).getOrElse(Set.empty[String])
      } else {
        (oldst.app, st.app) match {
          case (None, None)                 => Set.empty[String]
          case (None, Some(app))            => app.pending.all
          case (Some(app), None)            => app.pending.all
          case (Some(oldapp), Some(newapp)) => newapp.pending.diff(oldapp.pending).all
        }
      }
      val ifl_needs_update = if (screenChanged) {
        st.inflight.map(_.all).getOrElse(Set.empty[String])
      } else {
        (oldst.inflight, st.inflight) match {
          case (None, None)                 => Set.empty[String]
          case (None, Some(ifl))            => ifl.all
          case (Some(ifl), None)            => ifl.all
          case (Some(oldifl), Some(newifl)) => newifl.diff(oldifl).all
        }
      }
      val tf_needs_update = app_needs_update.union(ifl_needs_update)
      val q_needs_update = if (screenChanged) {
        oldst.quorum.keySet | st.quorum.keySet
      } else {
        (oldst.quorum.keySet | st.quorum.keySet)
          .filter(k => oldst.quorum.getOrElse(k, NoDNS(k)) != st.quorum.getOrElse(k, NoDNS(k)))
      }

      val pres_update_lines = if (pres_needs_update) {
        prestartStatements(st.prestart.getOrElse(Prestart.empty))
      } else {
        List.empty
      }
      val tf_update_lines = if (tf_needs_update.nonEmpty) {
        val update_applying = oldst.app.map(app => app.pending.filter(tf_needs_update)).getOrElse(TerraformPlan.empty)
        val update_inflight = oldst.inflight.getOrElse(TerraformPlan.empty).filter(tf_needs_update)
        (
          terraformPlanStatements(update_applying, InflightS()) ++
            terraformPlanStatements(update_inflight, IsDoneS())
        )
      } else {
        List.empty
      }
      val q_update_lines = if (q_needs_update.nonEmpty) {
        q_needs_update.map(k => quorumStatement(st.quorum.getOrElse(k, NoDNS(k))))
      } else {
        List.empty
      }

      val extra_update_lines = if (screenChanged) {
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

      val update_lines = pres_update_lines ++ tf_update_lines ++ q_update_lines ++ extra_update_lines

      val print_update = update_lines.foldLeftM(DrawingState(st))(((a, b) => update_element(a, b)))
      CLIMagic.sweep(screenChanged) *>
        print_update
          .flatMap(update_ticker)
          .map(_.recoverState)
          .flatMap(st =>
            CLIMagic
              .sweep()
              .map(_ => if (screenChanged) st.copy(screenSize = CLIMagic.consoleRows) else st)
          )
    }

    def beginIterPrint(prestate: Prestart): IO[Unit] = {
      val initialState = State.empty.copy(prestart = Some(prestate))

      for {
        _ <- CLIMagic.setupCLI()
        st <- init_draw(initialState, Apply(TerraformPlan.empty, Map.empty))
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
  def prestartLine(name: String, state: PrestartState): String = {
    state match {
      case LogTUI.Printer.Systemdnotstarted => s"$name:\t@|bold Waiting for systemd start|@"
      case LogTUI.Printer.Dnsnotresolving   => s"$name:\t@|green Service started|@           "
      case LogTUI.Printer.Done              => s"$name:\t@|green Service started and DNS resolves|@     "
      case LogTUI.Printer.Missing           => s"$name:\t@|faint ...|@                                 "
      case LogTUI.Printer.AlreadyResponding => s"$name:\t@|green Is active|@" // TODO asserts too much?
    }
  }

  def componentLine(rawName: String, target: PlanTarget, stage: PlanStage): String = {
    val name = TerraformMagic.res_name(rawName)
    val verb = target match {
      case CreateT() => "Create"
      case UpdateT() => "Update"
      case ReadT()   => "Receive"
      case DeleteT() => "Delete"
      case NoopT()   => "Do nothing"
    }
    stage match {
      case WaitingS()  => s"@|bold ${verb} $name|@  ".padTo(50, '-') + " @|faint Waiting|@                 "
      case InflightS() => s"@|bold ${verb} $name|@  ".padTo(50, '-') + " @|bold In process...|@           "
      case IsDoneS()   => s"@|bold $verb $name|@  ".padTo(50, '-') + " @|green Successful|@                             "
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
