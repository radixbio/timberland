package com.radix.utils.helm.http4s

import scala.concurrent.duration._
import cats.effect.{ContextShift, IO}
import org.scalacheck.util.Pretty
import org.scalactic.Prettifier
import org.scalactic.source.Position
//import cats.implicits._
import org.http4s._
import org.http4s.client.blaze._
import org.scalacheck._
import org.scalatest._
import org.scalatestplus.scalacheck.{CheckerAsserting, Checkers}
import com.radix.utils.helm
import helm.ConsulOp
import logstage._

import scala.concurrent.{Await, ExecutionContext}
import com.dimafeng.testcontainers._
import org.testcontainers.containers.wait.strategy.Wait

class IntegrationSpec extends FlatSpec with Checkers with BeforeAndAfterAll with ForAllTestContainer {

  private[this] val logger = IzLogger()
  val ConsulPort = 8500

  override val container =
    FixedHostPortGenericContainer(
      "consul:1.6.1",
      exposedHostPort = ConsulPort,
      exposedContainerPort = ConsulPort,
      command = "agent -dev -client=0.0.0.0".split(" "),
      waitStrategy = Wait.forHttp("/")
    )

  val baseUrl: Uri =
    Uri.fromString(s"http://127.0.0.1:$ConsulPort").toOption.get

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val cs: ContextShift[IO] = IO.contextShift(implicitly[ExecutionContext])
  val arb = Arbitrary(Gen.identifier suchThat (x => x.length > 1 && !x.contains("")))

  implicit val blaze = BlazeClientBuilder[IO](implicitly[ExecutionContext]).resource
  val i = new Http4sConsulClient[IO](baseUrl)
  "consul" should "work" in check({ (k: String, v: Array[Byte]) =>
    val testprog = for {
      _ <- helm.run(i, ConsulOp.kvSet(k, v))
      v1 <- helm.run(i, ConsulOp.kvGetRaw(k, None, None))
      _ <- IO(assert(v1.value.get === v))
      ks <- helm.run(i, ConsulOp.kvListKeys(""))
      _ <- IO(assert(ks.contains(k)))
      _ <- helm.run(i, ConsulOp.kvDelete(k))
      ks2 <- helm.run(i, ConsulOp.kvListKeys(""))
      _ <- IO(assert(!ks2.contains(k)))
    } yield (true)
    assert(testprog.unsafeRunSync())
    true
  })(
    implicitly[PropertyCheckConfiguration],
    implicitly[Boolean => Prop],
    arb,
    implicitly[Shrink[String]],
    implicitly[String => Pretty],
    Arbitrary.arbContainer[Array, Byte],
    implicitly[Shrink[Array[Byte]]],
    implicitly[Array[Byte] => Pretty],
    implicitly[Prettifier],
    implicitly[Position]
  )
//  })(
//    implicitly,
//    implicitly,
//    implicitly,
//    implicitly,
////    Arbitrary.arbContainer[Array, Byte],
////    implicitly,
////    implicitly,
//    implicitly[CheckerAsserting[EntityDecoder[IO, Array[Byte]]]],
//    implicitly,
//    implicitly
//  )
}
