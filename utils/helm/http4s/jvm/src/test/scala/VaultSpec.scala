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
  override val containerDef: ContainerDef = VaultContainer.Def("registry.gitlab.com/radix-labs/devops/vault", rootToken)

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

  it should "register and enable the oauth plugin" in { vault =>
    (for {
      registerResponse <- vault.session.registerPlugin(
        Secret(),
        "oauth2",
        RegisterPluginRequest("aece93ff2302b7ee5f90eebfbe8fe8296f1ce18f084c09823dbb3d3f0050b107", "oauth2"))
      _ <- IO(registerResponse should be(Right()))
      enableResponse <- vault.session.enableSecretsEngine("oauth2/google", EnableSecretsEngine("oauth2"))
      result <- IO(enableResponse should be(Right()))
    } yield result).unsafeRunSync()
  }

  it should "fail to enable a plugin with an invalid checksum" in { vault =>
    (for {
      registerResponse <- vault.session.registerPlugin(
        Secret(),
        "oauth2-bad",
        RegisterPluginRequest("0000000000000000000000000000000000000000000000000000000000000000", "oauth2"))
      _ <- IO(registerResponse should be (Right()))
      enableResponse <- vault.session.enableSecretsEngine("oauth2/google-bad", EnableSecretsEngine("oauth2-bad"))
      result <- IO(enableResponse should be(Left(VaultErrorResponse(Vector("checksums did not match")))))
    } yield result).unsafeRunSync()
  }
}
