package com.radix.utils.helm

import com.radix.utils.helm.NomadHCL.syntax.{ConsulConnectShim, SidecarServiceShim, UpstreamShim}
//import cats.data._
import cats.implicits._
//import cats.syntax.option._
import cats.Apply
import shapeless._
import shapeless.labelled._
import scala.language.implicitConversions

object NomadHCL {

  trait HCLFields[T] {
    def show(t: T): List[String]
  }

  trait HCLAttr[T] {
    def show(t: T): Option[String]
  }

  trait HCLAble[T] {
    def show(t: T): String
  }

  object defs {

    import syntax.{GroupShim, TaskShim}

    sealed trait HCLClause[T]

    sealed trait HCLFmt

    implicit def SubtypeToTypeclassHCLFmt[T <: HCLFmt]: HCLClause[T] = null

    implicit def ClausesCanBeLists[T: HCLClause]: HCLClause[List[T]] = null

    implicit def ClausesCanBeOptional[T: HCLClause]: HCLClause[Option[T]] = null

    case class Constraint(attribute: String = "", operator: String = "=", value: String = "") extends HCLFmt

    case class Update(
      max_parallel: Int = 0,
      health_check: String = "checks",
      min_healthy_time: String = "10s",
      healthy_deadline: String = "5m",
      progress_deadline: String = "10m",
      auto_revert: Boolean = false,
      //                      auto_promote: Boolean = false,
      canary: Int = 0,
      stagger: String = "30s",
    ) extends HCLFmt

    case class Target(value: String = "", percent: Int = 0) extends HCLFmt

    case class Spread(attribute: Option[String], target: Option[Target], weight: Int = 0) extends HCLFmt

    case class EphemeralDisk(migrate: Boolean = false, size: Int = 300, sticky: Boolean = false) extends HCLFmt

    case class Restart(attempts: Int = 2, delay: String = "15s", interval: String = "30m", mode: String = "fail")
        extends HCLFmt

    case class DispatchPayload(file: String = "") extends HCLFmt

    case class Artifact(
      destination: String = "local/",
      mode: String = "any",
      options: Option[Map[String, String]] = None,
      source: String,
    ) extends HCLFmt

    case class Template(
      change_mode: String = "restart",
      change_signal: Option[String] = None,
      data: Option[String] = None,
      destination: String,
      env: Boolean = false,
      left_delimiter: String = "{{",
      perms: String = "644",
      right_delimiter: String = "}}",
      source: Option[String] = None,
      splay: String = "5s",
    ) extends HCLFmt

    case class Logs(max_files: Int = 10, max_file_size: Int = 10) extends HCLFmt

    case class Port(static: Option[Int] = None) extends HCLFmt

    case class Network(mbits: Option[Int] = None, ports: List[syntax.PortShim], mode: Option[String] = None)
        extends HCLFmt

    case class CheckRestart(limit: Int = 0, grace: String = "1s", ignore_warnings: Boolean = false) extends HCLFmt

    case class Check(
      address_mode: String = "host",
      args: Option[List[String]] = None,
      check_restart: Option[CheckRestart] = None,
      command: Option[String] = None,
      grpc_service: Option[String] = None,
      grpc_use_tls: Option[Boolean] = Some(true),
      initial_status: String = "critical",
      interval: String,
      method: Option[String] = Some("GET"),
      name: Option[String] = None,
      path: Option[String] = None,
      port: Option[String] = None,
      protocol: String = "http",
      timeout: String,
      `type`: String,
      tls_skip_verify: Boolean = false,
    )
    //TODO Header format serialization
        extends HCLFmt

    case class Service(
      check: Option[List[Check]] = None,
      name: Option[String] = None,
      task: Option[String] = None,
      port: Option[String] = None,
      tags: Option[List[String]] = None,
      canary_tags: Option[List[String]] = None,
      address_mode: String = "auto",
      connect: Option[ConsulConnectShim] = None,
    ) extends HCLFmt

    case class ConsulConnect(
      sidecar_service: SidecarServiceShim = SidecarServiceShim(SidecarService()),
      native: Option[Boolean] = None,
    ) extends HCLFmt

    case class SidecarService(
      tags: Option[List[String]] = None,
      port: Option[String] = None,
      proxy: Option[Proxy] = None,
    ) extends HCLFmt

    case class Proxy(
      local_service_address: Option[String] = None,
      local_service_port: Option[Int] = None,
      upstreams: List[UpstreamShim],
    ) extends HCLFmt

    case class Upstream(destination_name: String, local_bind_port: Int) extends HCLFmt

    case class Device(
      name: String,
      count: Int = 1,
      constraint: Option[List[Constraint]] = None,
      affinity: Option[List[Affinity]] = None,
    ) extends HCLFmt

    case class Resources(
      cpu: Int = 100,
      memory: Int = 300,
      network: Option[Network] = None,
      device: Option[Device] = None,
    ) extends HCLFmt

    case class Task(
      artifact: Option[Artifact] = None,
      config: Option[DockerConfig] = None, //TODO add support for raw_exec, qemu, and jvm
      constraint: Option[List[Constraint]] = None,
      affinity: Option[List[Affinity]] = None,
      dispatch_payload: Option[DispatchPayload] = None,
      driver: String = "docker",
      env: Option[Map[String, String]] = None,
      kill_timeout: String = "5s",
      kill_signal: String = "SIGINT",
      leader: Boolean = false,
      logs: Option[Logs] = None,
      meta: Option[Map[String, String]] = None,
      resources: Resources = Resources(),
      services: Option[List[syntax.ServiceShim]] = None,
      shutdown_delay: String = "0s",
      user: Option[String] = None,
      template: Option[List[syntax.TemplateShim]] = None,
      vault: Option[Vault] = None,
    ) extends HCLFmt

    case class DockerConfig(
      image: Option[String] = None,
      args: Option[List[String]] = None,
      //                            auth: Option,
      auth_soft_fail: Option[Boolean] = None,
      command: Option[String] = None,
      dns_search_domains: Option[List[String]] = None,
      dns_options: Option[List[String]] = None,
      dns_servers: Option[List[String]] = Some(List("127.0.0.1")),
      entrypoint: Option[List[String]] = None,
      extra_hosts: Option[List[String]] = None,
      force_pull: Option[Boolean] = None,
      hostname: Option[String] = None,
      interactive: Option[Boolean] = None,
      sysctl: Option[Map[String, String]] = None,
      ulimit: Option[Map[String, String]] = None,
      privileged: Option[Boolean] = None,
      ipc_mode: Option[String] = None,
      ipv4_address: Option[String] = None,
      ipv6_address: Option[String] = None,
      labels: Option[Map[String, String]] = None,
      load: Option[String] = None,
      logging: Option[Map[String, String]] = None,
      mac_address: Option[String] = None,
      network_aliases: Option[List[String]] = None,
      network_mode: Option[String] = None,
      pid_mode: Option[String] = None,
      port_map: Option[Map[String, Int]] = None,
      security_opt: Option[List[String]] = None,
      shm_size: Option[Int] = None,
      storage_opt: Option[Map[String, String]] = None,
      tty: Option[Boolean] = None,
      uts_mode: Option[String] = None,
      userns_mode: Option[String] = None,
      volumes: Option[List[String]] = None,
      volume_driver: Option[String] = None,
      work_dir: Option[String] = None,
      //                            mounts: Option[List[String]] = None,
      //                           devices:
      cap_add: Option[List[String]] = None,
      cpu_hard_limit: Option[Boolean] = None,
      cpu_cfs_period: Option[Int] = None,
      advertise_ipv6_address: Option[Boolean] = None,
      readonly_rootfs: Option[Boolean] = None,
      pids_limit: Option[Int] = None,
    ) extends HCLFmt

    case class Group(
      constraint: Option[List[Constraint]] = None,
      affinity: Option[List[Affinity]] = None,
      spread: Option[List[Spread]] = None,
      count: Int = 1,
      ephemeral_disk: Option[EphemeralDisk] = None,
      meta: Option[Map[String, String]] = None,
      migrate: Option[Migrate] = None,
      reschedule: Option[Reschedule] = None,
      restart: Option[Restart] = None,
      task: List[TaskShim],
      vault: Option[Vault] = None,
      network: Option[Network] = None,
      services: Option[List[syntax.ServiceShim]] = None,
    ) extends HCLFmt

    case class Migrate(
      max_parallel: Int = 1,
      health_check: String = "checks",
      min_healthy_time: String = "10s",
      healthy_deadline: String = "5m",
    ) extends HCLFmt

    case class Parameterized(
      meta_optional: Option[List[String]],
      meta_required: Option[List[String]],
      payload: String = "optional",
    ) extends HCLFmt

    case class Periodic(cron: String, prohibit_overlap: Boolean = false, time_zone: String = "UTC") extends HCLFmt

    case class Reschedule(
      attempts: Option[Int],
      interval: Option[String] = None,
      delay: Option[String] = Some("30s"),
      delay_function: Option[String] = Some("exponential"),
      max_delay: Option[String] = Some("1h"),
      unlimited: Option[Boolean] = Some(true),
    ) extends HCLFmt

    case class Vault(
      change_mode: String = "restart",
      change_signal: Option[String],
      env: Boolean = true,
      policies: Option[List[String]] = None,
    ) extends HCLFmt

    case class Affinity(attribute: String = "", operator: String = "=", value: String = "", weight: Int = 50)
        extends HCLFmt

    case class Job(
      all_at_once: Boolean = false,
      constraint: Option[List[Constraint]] = None,
      affinity: Option[List[Affinity]] = None,
      spread: Option[List[Spread]] = None,
      datacenters: List[String] = List("dc1"),
      group: List[GroupShim],
      meta: Option[Map[String, String]] = None,
      migrate: Option[Migrate] = None,
      namespace: String = "default",
      parameterized: Option[Parameterized] = None,
      periodic: Option[Periodic] = None,
      priority: Int = 50,
      region: String = "global",
      reschedule: Option[Reschedule] = None,
      `type`: String = "service",
      update: Option[Update] = None,
      vault: Option[Vault] = None,
      vault_token: Option[String] = None,
    ) extends HCLFmt

  }

  object primitives {

    implicit val hnilEncoderClause: defs.HCLClause[HNil] = null
    implicit val hnilSpecialClause: syntax.HCLSpecial[HNil] = null
    implicit val hnilField: HCLFields[HNil] = new HCLFields[HNil] {
      override def show(t: HNil): List[String] = List.empty[String]
    }
    implicit val hnilAble: HCLAble[HNil] = new HCLAble[HNil] {
      override def show(t: HNil): String = ""
    }
    implicit val hnilAttr: HCLAttr[HNil] = new HCLAttr[HNil] {
      override def show(t: HNil): Option[String] = None
    }

    implicit object HCLBasicString extends HCLAttr[String] {
      override def show(t: String): Option[String] = {

        t match {
          case "" => None
          case els => {
            if (els.indexOf("<<EOF") >= 0) {
              Some(els)
            } else {
              Some("\"" + els + "\"")
            }

          }
        }
      }
    }

    implicit object HCLBasicBool extends HCLAttr[Boolean] {
      override def show(t: Boolean): Option[String] = {
        Some(if (t) "true" else "false")
      }
    }

    implicit object HCLBasicInt extends HCLAttr[Int] {
      override def show(t: Int): Option[String] = Some(t.toString)
    }

  }

  object derivations {

    // import primitives._
    object simple {
      implicit def HCLFieldsOptionDerive[T](implicit T: HCLFields[T]): HCLFields[Option[T]] = new HCLFields[Option[T]] {
        override def show(t: Option[T]): List[String] = {
          t match {
            case None      => List.empty[String]
            case Some(int) => T.show(int)
          }
        }
      }

      implicit def HCLFieldsListDerive[T](implicit T: HCLFields[T]): HCLFields[List[T]] =
        new HCLFields[List[T]] {
          override def show(t: List[T]): List[String] = {
            t.flatMap(T.show)
          }
        }

      implicit def HCLAttrOptionDerive[T](implicit T: HCLAttr[T]): HCLAttr[Option[T]] = new HCLAttr[Option[T]] {
        override def show(t: Option[T]): Option[String] = {
          t.flatMap(T.show)
        }
      }

      //KV-maps in HCL lose all type information
      implicit def HCLAttrMapDerive[K, V](implicit K: HCLAttr[K], V: HCLAttr[V]): HCLAttr[Map[K, V]] =
        new HCLAttr[Map[K, V]] {
          override def show(t: Map[K, V]): Option[String] = {
            if (t.isEmpty) None
            else {
              t.toList
                .map({ case (k, v) => Apply[Option].product(K.show(k), V.show(v)) }) // Both Key and Value must exist
                .sequence
                .map(_.map({ case (k, v) => s"$k = $v" }))
                .map(_.mkString(",\n"))
                .map(x => s"{$x}")
            }
          }
        }

      //Primitive type lists form a []-like list
      implicit def HCLAttrListDerive[T](implicit T: HCLAttr[T]): HCLAttr[List[T]] = new HCLAttr[List[T]] {
        override def show(t: List[T]): Option[String] = {
          t.map(T.show).sequence.map(_.mkString(",")).map(x => "[" + x + "]")
        }
      }

      //Non-primitive types do not occur within []-like lists, but rather are simply concatenated
      implicit def HCLAbleList[T](implicit T: HCLAble[T]): HCLAble[List[T]] =
        new HCLAble[List[T]] {
          override def show(t: List[T]): String = {
            t.map(T.show).mkString("\n")
          }
        }

      implicit def HCLAbleOption[T](implicit T: HCLAble[T]): HCLAble[Option[T]] = new HCLAble[Option[T]] {
        override def show(t: Option[T]): String = {
          t.map(T.show).getOrElse("")
        }
      }
    }

  }

  /**
   * This object is from moving to Attr -> Fields (stuff with an "=" sign) -> Finished Block
   */
  object hclhlist extends lowprio {

    implicit def LiftAttrsIntoElements[H, K, T <: HList](implicit
      H: Lazy[HCLAttr[H]],
      W: Witness.Aux[K],
      T: HCLFields[T],
    ): HCLFields[FieldType[K, H] :: T] = {
      new HCLFields[FieldType[K, H] :: T] {
        override def show(t: FieldType[K, H] :: T): List[String] = {
          t match {
            case h :: tail =>
              H.value
                .show(h)
                .map(hd => s"${W.value.toString.drop(7).dropRight(1)} = $hd" :: T.show(tail))
                .getOrElse(T.show(tail))
          }
        }
      }
    }

    implicit def LGLiftAble[A, R](implicit gen: LabelledGeneric.Aux[A, R], rencoder: HCLAble[R]): HCLAble[A] = {
      new HCLAble[A] {
        override def show(t: A): String = rencoder.show(gen.to(t))
      }
    }

    implicit def LGLiftAttrs[A, R](implicit gen: LabelledGeneric.Aux[A, R], rencoder: HCLAttr[R]): HCLAttr[A] = {
      new HCLAttr[A] {
        override def show(t: A): Option[String] = rencoder.show(gen.to(t))
      }
    }

    implicit def LGLiftFields[A, R](implicit gen: LabelledGeneric.Aux[A, R], rencoder: HCLFields[R]): HCLFields[A] = {
      new HCLFields[A] {
        override def show(t: A): List[String] = rencoder.show(gen.to(t))
      }
    }

    implicit def HCLFieldsSpecialConcatDerivation[H, K, T <: HList](implicit
      H: Lazy[HCLFields[H]],
      W: Witness.Aux[K],
      ev: syntax.HCLSpecial[H],
      ev2: HCLAble[H],
      T: HCLFields[T],
    ): HCLFields[FieldType[K, H] :: T] = {
      new HCLFields[FieldType[K, H] :: T] {
        override def show(t: FieldType[K, H] :: T): List[String] = {
          t match {
            case h :: tail => {
              ev2.show(h).split("\n").map("  " + _ + "\n").mkString("") :: T.show(tail)
            }
          }
        }
      }
    }

    implicit def HCLSpecialsDerive[H, K, T <: HList](implicit
      H: Lazy[syntax.HCLSpecial[H]],
      W: Witness.Aux[K],
      T: syntax.HCLSpecial[T],
    ): syntax.HCLSpecial[FieldType[K, H] :: T] = null

    implicit def HCLFieldsNormalDerivation[H, K, T <: HList](implicit
      H: Lazy[HCLFields[H]],
      W: Witness.Aux[K],
      ev: defs.HCLClause[H],
      T: HCLFields[T],
    ): HCLFields[FieldType[K, H] :: T] = {
      new HCLFields[FieldType[K, H] :: T] {
        override def show(t: FieldType[K, H] :: T): List[String] = {
          t match {
            case h :: tail => {
              val inside = H.value.show(h).map("  " + _).mkString("\n")
              if (inside.stripLineEnd.replaceAll(" ", "") != "") {
                s"""
                   |${W.value.toString.drop(7).dropRight(1)} {
                   |${inside}
                   |}
                   |""".stripMargin.split("\n").map("  " + _).mkString("\n") + "\n" :: T.show(tail)
              } else T.show(tail)
            }
          }
        }
      }
    }

    implicit def HCLClausesDerive[H, K, T <: HList](implicit
      H: Lazy[defs.HCLClause[H]],
      W: Witness.Aux[K],
      T: defs.HCLClause[T],
    ): defs.HCLClause[FieldType[K, H] :: T] = null

  }

  trait lowprio {}

  object syntax {
    // The following commented 5 lines are silently removed by Intellij as part of
    // import optimization, but results in compilation error.
    // import defs._
    // import primitives._
    // import derivations._
    // import simple._
    // import hclhlist._

    import defs._
    import primitives._
    import derivations._
    import simple._
    import hclhlist._

    sealed trait HCLSpecial[T]

    final implicit def ShimSubtypeToTypeclassEvidence[T <: Shim]: HCLSpecial[T] = null

    final implicit def ShimsDeriveListEvidence[T: HCLSpecial]: HCLSpecial[List[T]] = null

    final implicit def ShimsDeriveOptionEvidence[T: HCLSpecial]: HCLSpecial[Option[T]] = null

    sealed trait Shim

    case class JobShim(name: String, job: Job) extends Shim

    object JobShim {
      implicit val attrib: HCLAble[JobShim] = new HCLAble[JobShim] {
        override def show(t: JobShim): String = {
          val fields = implicitly[Lazy[HCLFields[Job]]].value.show(t.job)
          s"""
             |job "${t.name}" {
             |${fields.map("  " + _).mkString("\n")}
             |}
             |""".stripMargin
        }
      }
    }

    case class GroupShim(name: String, group: Group) extends Shim

    object GroupShim {
      implicit val show: HCLAble[GroupShim] = new HCLAble[GroupShim] {
        override def show(t: GroupShim): String = {
          val group = implicitly[Lazy[HCLFields[Group]]].value.show(t.group)
          s"""
             |group "${t.name}" {
             |${group.map("  " + _).mkString("\n")}
             |}
             |""".stripMargin
        }
      }
    }

    case class TaskShim(name: String, task: Task) extends Shim

    object TaskShim {
      implicit val show: HCLAble[TaskShim] = new HCLAble[TaskShim] {
        override def show(t: TaskShim): String = {
          val task = implicitly[Lazy[HCLFields[Task]]].value.show(t.task)
          s"""
             |task "${t.name}" {
             |${task.map("  " + _).mkString("\n")}
             |}
             |""".stripMargin
        }
      }
    }

    case class PortShim(name: String, port: Port) extends Shim

    object PortShim {
      implicit val show: HCLAble[PortShim] = new HCLAble[PortShim] {
        override def show(t: PortShim): String = {
          val port = implicitly[Lazy[HCLFields[Port]]].value.show(t.port)
          s"""
             |port "${t.name}" {
             |${port.mkString("\n")}
             |}
             |""".stripMargin
        }
      }
    }

    case class ConsulConnectShim(consulConnect: ConsulConnect) extends Shim

    object ConsulConnectShim {
      implicit val show: HCLAble[ConsulConnectShim] = new HCLAble[ConsulConnectShim] {
        override def show(t: ConsulConnectShim): String = {
          val connect = implicitly[Lazy[HCLFields[ConsulConnect]]].value.show(t.consulConnect)
          s"""
             |connect {
             |  ${connect.mkString("\n")}
             |}
             |""".stripMargin
        }
      }
    }

    case class SidecarServiceShim(sidecarService: SidecarService) extends Shim

    object SidecarServiceShim {
      implicit val show: HCLAble[SidecarServiceShim] = new HCLAble[SidecarServiceShim] {
        override def show(t: SidecarServiceShim): String = {
          val sidecarService = implicitly[Lazy[HCLFields[SidecarService]]].value.show(t.sidecarService)
          s"""
             |sidecar_service {
             |  ${sidecarService.mkString("\n")}
             |}
             |""".stripMargin
        }
      }
    }

    case class UpstreamShim(upstream: Upstream) extends Shim

    object UpstreamShim {
      implicit val show: HCLAble[UpstreamShim] = new HCLAble[UpstreamShim] {
        override def show(t: UpstreamShim): String = {
          val upstream = implicitly[Lazy[HCLFields[Upstream]]].value.show(t.upstream)
          s"""
             |upstreams {
             |  ${upstream.mkString("\n  ")}
             |}
             |""".stripMargin
        }
      }
    }

    case class ServiceShim(service: Service) extends Shim

    object ServiceShim {
      implicit val show: HCLAble[ServiceShim] = new HCLAble[ServiceShim] {
        override def show(t: ServiceShim): String = {
          val service = implicitly[Lazy[HCLFields[Service]]].value.show(t.service)
          s"""
             |service {
             |${service.mkString("\n")}
             |}
             |""".stripMargin
        }
      }
    }

    case class TemplateShim(template: Template) extends Shim

    object TemplateShim {
      implicit val show: HCLAble[TemplateShim] = new HCLAble[TemplateShim] {
        override def show(t: TemplateShim): String = {
          val template = implicitly[Lazy[HCLFields[Template]]].value.show(t.template)
          s"""
             |template {
             |${template.mkString("\n")}
             |}
             |""".stripMargin
        }
      }
    }

    implicit class AsHCL[T](in: T)(implicit ev: HCLAble[T]) {
      def asHCL: String = ev.show(in)
    }

    implicit def inside(T: JobShim): Job = T.job

    implicit def inside(T: TaskShim): Task = T.task

    implicit def inside(T: GroupShim): Group = T.group

    def job(name: String)(job: Job): JobShim = {
      JobShim(name, job)
    }

    def group(name: String)(group: Group): GroupShim = {
      GroupShim(name, group)
    }

    def task(name: String)(task: Task): TaskShim = {
      TaskShim(name, task)
    }
  }

}

// Sample test.
// The following commented 7 lines are silently removed by Intellij as part of
// import optimization, but results in compilation error.
// import NomadHCL._
// import primitives._
// import derivations._
// import defs._
// import simple._
// import hclhlist._
// import NomadHCL.syntax._
/*
import NomadHCL._
import primitives._
import derivations._
import defs._
import simple._
import hclhlist._
import NomadHCL.syntax._

object Tester extends App {
//  import scala.reflect.runtime.universe._

  val job1: Job = job("foo") {
    Job(group = List(group("bar") {
      Group(task = List(task("arst")(Task())))
    }))
  }
  val job2: JobShim = job("foo") {
    Job(group = List(group("bar") {
      Group(task = List(task("arst")(Task(
        config=Some(Map("image" -> "hello-world:latest"))
        ))))
    }) , `type` = "batch")
  }

  println(implicitly[Lazy[HCLAble[JobShim]]].value.show(job2))
}
 */
