package com.radix.timberland_svc.timberlandService

import scala.concurrent.duration._
import cats.effect.{IO, IOApp}
import com.radix.timberland.runtime._

object timberlandService extends IOApp {
  val serviceController = Services.serviceController
  override def run(args: List[String]): IO[Nothing] =
    for {
      _ <- IO.sleep(1.hour)
      _ <- serviceController.restartConsulTemplate()
      nothing <- run(args)
    } yield nothing
}
