package com.radix.utils.helm.http4s

import scala.concurrent.duration._
import cats.effect.{ContextShift, IO}
import cats.implicits._
import org.http4s._
import org.http4s.client.blaze._
import org.scalacheck._
import org.scalatest._
import org.scalatest.enablers.CheckerAsserting
import org.scalatest.prop._
import com.radix.utils.helm
import helm.ConsulOp
import logstage._

import scala.concurrent.Await
import com.dimafeng.testcontainers._
import org.testcontainers.containers.wait.strategy.Wait


class IntegrationSpec extends FlatSpec with Matchers with Checkers with BeforeAndAfterAll with ForAllTestContainer {

  private[this] val logger = IzLogger()
  val ConsulPort = 8500

  override val container =
    FixedHostPortGenericContainer("consul:1.6.1", exposedHostPort = ConsulPort,exposedContainerPort = ConsulPort, command = "agent -dev -client=0.0.0.0".split(" "), waitStrategy = Wait.forHttp("/"))

  val baseUrl: Uri =
    Uri.fromString(s"http://127.0.0.1:${ConsulPort}").toOption.get

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val cs: ContextShift[IO] = IO.contextShift(global)

  val i =
    BlazeClientBuilder[IO](global).resource.map(new Http4sConsulClient[IO](baseUrl, _)).allocated.unsafeRunSync()._1
  "consul" should "work" in check { (k: String, v: Array[Byte]) =>
    val testprog = for {
      _ <- helm.run(i, ConsulOp.kvSet(k, v))
      v1 <- helm.run(i, ConsulOp.kvGetRaw(k, None, None))
      _ <- IO(v1.value.get should be(v))
      ks <- helm.run(i, ConsulOp.kvListKeys(""))
      _ <- IO(ks should contain(k))
      _ <- helm.run(i, ConsulOp.kvDelete(k))
      ks2 <- helm.run(i, ConsulOp.kvListKeys(""))
      _ <- IO(ks2 should not contain (k))
    } yield (true)
    assert(testprog.unsafeRunSync())
    true
  }(
    implicitly,
    implicitly,
    Arbitrary(Gen.identifier suchThat (x => x.length > 0 && !x.contains(""))),
    implicitly,
    implicitly,
    Arbitrary.arbContainer[Array, Byte],
    implicitly,
    implicitly,
    implicitly[CheckerAsserting[EntityDecoder[IO, Array[Byte]]]],
    implicitly,
    implicitly
  )
}
