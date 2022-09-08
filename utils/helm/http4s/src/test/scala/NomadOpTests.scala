package com.radix.utils.helm.http4s.test

import cats.effect.IO
import com.dimafeng.testcontainers.{FixedHostPortGenericContainer, ForAllTestContainer}
import com.radix.utils.helm._
import com.radix.utils.helm.http4s.Http4sNomadClient
import org.http4s.Uri
import org.http4s.client.blaze.BlazeClientBuilder
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.testcontainers.containers.wait.strategy.Wait

import scala.concurrent.ExecutionContext.Implicits.global
//import com.radix.utils.helm.NomadHCL._
//import primitives._
//import derivations._
import com.radix.utils.helm.NomadHCL._
import com.radix.utils.helm.NomadHCL.defs._
//import simple._
//import hclhlist._
import com.radix.utils.helm.NomadHCL.syntax._

// run: nomad agent -dev , to run nomad locally before running test from sbt command line.

class NomadOpTests extends AnyFlatSpec with ForAllTestContainer {

  override val container =
    FixedHostPortGenericContainer(
      "registry.gitlab.com/radix-labs/devops/nomad:latest",
      exposedHostPort = 4646,
      exposedContainerPort = 4646,
      waitStrategy = Wait.forHttp("/"),
      env = Map("NOMAD_LOCAL_CONFIG" -> """{"bind_addr": "0.0.0.0"}"""),
      command = List("agent", "-dev")
    )
  val baseUrl = Uri.fromString("http://127.0.0.1:4646").toOption.get
  implicit val cs = IO.contextShift(global)

  def interp[A](op: NomadOp[A]): IO[A] =
    BlazeClientBuilder[IO](global).resource.use(client => {
      new Http4sNomadClient(baseUrl, client).apply(op)
    })

  val hcl = raw"""job "hello1" {
           |  datacenters = ["dc1"]
           |  type = "batch"
           |  group "example" {
           |    count = 1
           |    task "hello" {
           |      driver = "docker"
           |      config {
           |        image = "hello-world:latest"
           |      }
           |    }
           |  }
           |}
           |""".stripMargin

  val job2: JobShim = job("foo2") {
    Job(
      group = List(group("bar") {
        Group(
          task = List(
            task("arst")(
              Task(
                config = Some(DockerConfig(image = Some("hello-world:latest")))
              )
            )
          )
        )
      }),
      `type` = "batch"
    )
  }

  "nomadCreateJobFromHCL" should "return the response after creating a job in Nomad" in {

    val runCreateJob = interp(NomadOp.NomadCreateJobFromHCL(job2)).unsafeRunSync
    assert(runCreateJob.value.head.evalId != "")
  }

  it should "return the description of jobs in Nomad that match namespace" in {

    val nomadJobs = interp(NomadOp.NomadListJobs()).unsafeRunSync
    println(nomadJobs.head)
    assert(nomadJobs.head.id != "")
  }

}
/*
class NomadOpTests extends FlatSpec with Matchers with TypeCheckedTripleEquals {

  val I = Interpreter.prepare[NomadOp, IO]

  "getJson" should "return none right when get returns None" in {
    val interp = for {
      _ <- I.expectU[QueryResponse[Option[Array[Byte]]]] {
        case NomadOp.KVGetRaw("foo", None, None) => IO.pure(QueryResponse(None, -1, true, -1))
      }
    } yield ()
    interp.run(kvGetJson[Json]("foo", None, None)).unsafeRunSync should equal(Right(QueryResponse(None, -1, true, -1)))
  }

  it should "return a value when get returns a decodeable value" in {
    val interp = for {
      _ <- I.expectU[QueryResponse[Option[Array[Byte]]]] {
        case NomadOp.KVGetRaw("foo", None, None) => IO.pure(QueryResponse(Some("42".getBytes), -1, true, -1))
      }
    } yield ()
    interp.run(kvGetJson[Json]("foo", None, None)).unsafeRunSync should equal(Right(QueryResponse(Some(jNumber(42)), -1, true, -1)))
  }

  it should "return an error when get returns a non-decodeable value" in {
    val interp = for {
      _ <- I.expectU[QueryResponse[Option[Array[Byte]]]] {
        case NomadOp.KVGetRaw("foo", None, None) => IO.pure(QueryResponse(Some("{".getBytes), -1, true, -1))
      }
    } yield ()
    interp.run(kvGetJson[Json]("foo", None, None)).unsafeRunSync should equal(Left("JSON terminates unexpectedly."))
  }
}
 */
