package com.radix.timberland.daemons

import cats.syntax.option._
import com.radix.utils.helm.NomadHCL.syntax.{
  GroupShim,
  JobShim,
  PortShim,
  ServiceShim,
  TaskShim,
  TemplateShim
}
import com.radix.utils.helm.NomadHCL.{HCLAble, defs}

trait Update {
  def stagger: String

  def max_parallel: Int

  def min_healthy_time: String

  def assemble: defs.Update =
    defs.Update(stagger = stagger,
                max_parallel = max_parallel,
                min_healthy_time = min_healthy_time)
}

trait Constraint {
  def operator: Option[String]

  def attribute: Option[String]

  def value: String

  def assemble: defs.Constraint = {
    (operator, attribute) match {
      case (Some(op), None) => defs.Constraint(operator = op, value = value)
      case (None, Some(attr)) =>
        defs.Constraint(attribute = attr, value = value)
      case (Some(op), Some(attr)) =>
        defs.Constraint(operator = op, attribute = attr, value = value)
      case (None, None) => throw new Error("Missing operator or attribute")
    }
  }
}

trait Template {
  def source: Option[String] = None

  def destination: String

  val data: Option[String] = None

  val perms: String = "644"

  def assemble: TemplateShim =
    TemplateShim(
      defs.Template(source = source,
                    destination = destination,
                    change_mode = change_mode,
                    data = data,
                    perms = perms))

  def change_mode: String = "noop"
}

trait Config {
  def image: String

  def hostname: String

  def entrypoint: Option[List[String]]

  def command: Option[String]

  def args: Option[List[String]]

  def port_map: Map[String, Int]

  def volumes: Option[List[String]]

  def cap_add: Option[List[String]] = List().some

  def ulimit: Option[Map[String, String]] = None

  def privileged: Option[Boolean] = false.some

  def work_dir: Option[String] = None

  def assemble: defs.DockerConfig =
    defs.DockerConfig(
      image = image,
      hostname = hostname.some,
      entrypoint = entrypoint,
      command = command,
      args = args,
      port_map = port_map.some,
      network_mode = network_mode.some,
      volumes = volumes,
      cap_add = cap_add,
      ulimit = ulimit,
      privileged = privileged,
      work_dir = work_dir
    )

  def network_mode: String = "weave"
}

trait Network {
  def networkPorts: Map[String, Option[Int]]

  def assemble: Option[defs.Network] = {
    val portShims: List[PortShim] =
      networkPorts.map(kv => PortShim(kv._1, defs.Port(kv._2))).to[List]
    //    for ((key, value) <- networkPorts) {
    //      val shim = PortShim(key, defs.Port(value.some))
    //      finalNetwork = defs.Network(port = shim)
    //    }
    val finalNetwork = defs.Network(ports = portShims)
    finalNetwork.some
  }
}

trait Resources {
  def cpu: Int

  def memory: Int

  def network: Network

  def assemble: defs.Resources =
    defs.Resources(cpu = cpu, memory = memory, network = network.assemble)
}

trait Check {
  def `type`: String

  def port: String

  def assemble: defs.Check =
    defs.Check(port = port.some,
               `type` = `type`,
               timeout = timeout,
               interval = interval)

  def interval: String = "10s"

  def timeout: String = "2s"
}

trait Service {
  def tags: List[String]

  def port: Option[String]

  def checks: List[Check]

  def name: Option[String] = None

  def assemble: ServiceShim = {
    val assembledChecks: Option[List[defs.Check]] = checks.map(_.assemble).some
    ServiceShim(
      defs.Service(tags = tags.some,
                   port = port,
                   name = name,
                   check = assembledChecks))
  }
}

trait Task {
  def name: String

  def driver = "docker"

  def templates: Option[List[Template]]

  def config: Config

  def resources: Resources

  def env: Option[Map[String, String]]

  def services: List[Service]

  def user: Option[String] = None

  def assemble: TaskShim = {
    val assembledServices = services.map(_.assemble).some
    val assembledTemplates = templates.getOrElse(List()).map(_.assemble).some
    (env, assembledTemplates) match {
      case (Some(e), None) =>
        TaskShim(name,
                 defs.Task(services = assembledServices,
                           config = config.assemble.some,
                           resources = resources.assemble,
                           env = e.some,
                           user = user))
      case (None, Some(t)) =>
        TaskShim(name,
                 defs.Task(services = assembledServices,
                           config = config.assemble.some,
                           resources = resources.assemble,
                           template = assembledTemplates,
                           user = user))
      case (Some(e), Some(t)) =>
        TaskShim(name,
                 defs.Task(services = assembledServices,
                           config = config.assemble.some,
                           resources = resources.assemble,
                           env = e.some,
                           template = assembledTemplates,
                           user = user))
      case (None, None) =>
        TaskShim(name,
                 defs.Task(services = assembledServices,
                           config = config.assemble.some,
                           resources = resources.assemble,
                           user = user))
    }

  }
}

trait Group {
  def name: String

  def constraints: Option[List[Constraint]]

  def count: Int

  def tasks: List[Task]

  def assemble: GroupShim = {
    val assembledConstraints: Option[List[defs.Constraint]] =
      constraints match {
        case Some(c) => c.map(_.assemble).some
        case None    => None

      }
    val assembledTasks = tasks.map(_.assemble)
    GroupShim(name,
              defs.Group(count = count,
                         constraint = assembledConstraints,
                         task = assembledTasks))

  }
}

/** A Nomad job description which can be used to assemble HCL and send it to Nomad
  * (Not sure how to fully document this since ScalaDocs has no standard for traits)
  */
trait Job {
  def name: String

  def datacenters: List[String]

  def update: Option[Update] = None

  def constraints: Option[List[Constraint]]

  def groups: List[Group]

  def `type`: Option[String] = "service".some

  def jobshim(): JobShim

  def assemble: defs.Job = {
    val assembledConstraints: Option[List[defs.Constraint]] =
      constraints match {
        case Some(c) => c.map(_.assemble).some
        case None    => None

      }
    val assembledGroups: List[GroupShim] = groups.map(_.assemble)

    update match {
      case Some(update) =>
        defs.Job(datacenters = datacenters,
                 update = update.assemble.some,
                 group = assembledGroups,
                 constraint = assembledConstraints,
                 `type` = `type`.getOrElse("service"))
      case None =>
        defs.Job(datacenters = datacenters,
                 group = assembledGroups,
                 constraint = assembledConstraints,
                 `type` = `type`.getOrElse("service"))
    }

  }
}