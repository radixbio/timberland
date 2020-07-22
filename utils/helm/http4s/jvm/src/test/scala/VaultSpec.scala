package com.radix.utils.helm.http4s.test

import cats.implicits._
import cats.effect.{ContextShift, IO}
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import org.scalatest._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s._
import com.radix.utils.helm.http4s.vault._
import com.radix.utils.helm.vault._

import scala.concurrent.ExecutionContext
import com.dimafeng.testcontainers.{ContainerDef, VaultContainer}

class VaultSpec extends fixture.FlatSpec with Matchers with TestContainerForAll {
  val rootToken: Option[String] = Some("radix")
  case class FixtureParam(session: Vault[IO])
  override val containerDef: ContainerDef = VaultContainer.Def("vault:latest", rootToken)

  override def withFixture(test: OneArgTest): Outcome = {
    val ec: ExecutionContext = ExecutionContext.global
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)
    val blazeBuilder: BlazeClientBuilder[IO] = BlazeClientBuilder[IO](ec)

    withContainers { vaultContainer =>
      val vault = vaultContainer.asInstanceOf[VaultContainer]
      val vaultBaseUrl = Uri.unsafeFromString(s"http://${vault.containerIpAddress}:${vault.mappedPort(8200)}")

      blazeBuilder.resource.use { blazeClient =>
        val vaultSession: Vault[IO] = new Vault[IO](rootToken, vaultBaseUrl, blazeClient)
        IO(super.withFixture(test.toNoArgTest(FixtureParam(vaultSession))))
      }.unsafeRunSync()
    }
  }

  "vault" should "be unsealed upon start" in { vault =>
    vault.session.sealStatus().flatMap { sealStatus =>
      IO(sealStatus should be(Right(SealStatusResponse(`sealed` = false, 1, 1, 0))))
    }.unsafeRunSync()
  }

}
