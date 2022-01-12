package com.radix.utils.helm.http4s.test

import cats.implicits._
import cats.effect.{ContextShift, IO, Resource}
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import org.scalatest._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s._
import com.radix.utils.helm.http4s.vault._
import com.radix.utils.helm.vault._

import scala.concurrent.ExecutionContext
import com.dimafeng.testcontainers.{ContainerDef, VaultContainer}
import org.http4s.client.Client
import org.scalatest.funspec.FixtureAnyFunSpec
import org.scalatest.matchers.should.Matchers

class VaultSpec extends FixtureAnyFunSpec with Matchers with TestContainerForAll {
  val rootToken: Option[String] = Some("radix")
  case class FixtureParam(session: Vault[IO])
  override val containerDef: ContainerDef = VaultContainer.Def("vault:latest", rootToken)

  override def withFixture(test: OneArgTest): Outcome = {
    val ec: ExecutionContext = ExecutionContext.global
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)
    implicit val blazeResource: Resource[IO, Client[IO]] = BlazeClientBuilder[IO](ec).resource

    withContainers { vaultContainer =>
      val vault = vaultContainer.asInstanceOf[VaultContainer]
      val vaultBaseUrl = Uri.unsafeFromString(s"http://${vault.containerIpAddress}:${vault.mappedPort(8200)}")

      val vaultSession: Vault[IO] = new Vault[IO](rootToken, vaultBaseUrl)
      IO(super.withFixture(test.toNoArgTest(FixtureParam(vaultSession)))).unsafeRunSync()
    }
  }

  "vault" should "be unsealed upon start" in { vault =>
    vault.session
      .sealStatus()
      .flatMap { sealStatus =>
        IO(sealStatus should be(Right(SealStatusResponse(`sealed` = false, 1, 1, 0))))
      }
      .unsafeRunSync()
  }

}
