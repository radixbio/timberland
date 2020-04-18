package com.radix.utils.helm.http4s.test

import com.radix.utils.helm
import helm._
import com.radix.utils.helm.http4s._
import cats.~>
import cats.effect.IO
import fs2.{Chunk, Stream}
//import org.http4s.Request
import org.http4s.{EntityBody, Header, Headers, Response, Status, Uri}
import org.http4s.client._
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest._
import org.scalatest.matchers.{BeMatcher, MatchResult}
import org.http4s.syntax.string.http4sStringSyntax
import scala.reflect.ClassTag

// Dummy tests that test the ability of http4sNomad client to parse the expected
// response from nomad API calls without actually connecting to nomad.

class Http4sNomadTests
    extends FlatSpec
    with Matchers
    with TypeCheckedTripleEquals {
  import Http4sNomadTests._

  import com.radix.utils.helm.NomadHCL.syntax._
  import com.radix.utils.helm.NomadHCL.defs._

  "nomadCreateJobFromHCL" should "succeed with some when the response is 200" in {
    val response = nomadResponse(Status.Ok,
                                 nomadCreateJobFromHCLReplyJson,
                                 nomadHeaders(555, false, 2))
    val nmd = constantNomad(response)
    helm
      .runNomad(nmd, NomadOp.nomadCreateJobFromHCL(JobShim("arst", Job(group = List.empty[GroupShim]))))
      .attempt
      .unsafeRunSync should ===(Right(nomadCreateJobFromHCLReturnValue))
  }

  it should "fail when the response is 500" in {
    val response = nomadResponse(Status.InternalServerError, "error")
    val nmd = constantNomad(response)
    helm
      .runNomad(nmd, NomadOp.nomadCreateJobFromHCL(JobShim("arst", Job(group = List.empty[GroupShim]))))
      .attempt
      .unsafeRunSync should be(nomadErrorException)
  }
}

// -----
/*
trait NomadClient[F[_]] {
  case class NomadResponse(status: Int, body: String, headers: NomadHeaders)
  def constantNomad(response: NomadResponse): NomadOp ~> F
  def nomadHeaders(index: Long,
                   knownLeader: Boolean,
                   lastContact: Long): NomadHeaders
  def nomadResponse(status: Int, s: String, headers: NomadHeaders): Response[F]
}
*/

object Http4sNomadTests {
  def constantNomad(response: Response[IO]): NomadOp ~> IO = {
    new Http4sNomadClient(Uri.uri("http://localhost:4646/v1/jobs"),
                          constantResponseClient(response),
                          None)
  }

  def nomadHeaders(index: Long,
                   knownLeader: Boolean,
                   lastContact: Long): Headers = {
    Headers(
      List(
        Header.Raw("X-Nomad-Index".ci, index.toString),
        Header.Raw("X-Nomad-KnownLeader".ci, knownLeader.toString),
        Header.Raw("X-Nomad-LastContact".ci, lastContact.toString)
      )
    )
  }

  def nomadResponse(status: Status,
                    s: String,
                    headers: Headers = Headers.empty): Response[IO] = {
    val responseBody = body(s)
    Response(status = status, body = responseBody, headers = headers)
  }

  def constantResponseClient(response: Response[IO]): Client[IO] = {
    import cats.effect.Resource
    Client(_ => Resource.liftF(IO.pure(response)))
  }

  def body(s: String): EntityBody[IO] =
    Stream.chunk(Chunk.bytes(s.getBytes("UTF-8"))) // YOLO

  val nomadCreateJobFromHCLReplyJson = """
      {
          "EvalID": "1234-5",
          "EvalCreateIndex": 12345,
          "JobModifyIndex": 54321,
          "Warnings": "Dummy warning",
          "Index": 32,
          "LastContact": 0,
          "KnownLeader": false
      }
  """

  val nomadCreateJobFromHCLReturnValue =
    QueryResponse(
      List(
        NomadCreateJobResponse("1234-5", 12345, 54321, "Dummy warning", 32, 0, false)
      ),
      32,
      false,
      0
    )

  // Some custom matchers here because ScalaTest's built-in matching doesn't handle Left(Throwable) well.
  // It has handling for thrown exceptions, but not just straight-up comparison.
  // Who knows, maybe I missed something and this is just redundant. Ah well.

  class LeftExceptionMatcher[E <: Exception: ClassTag](exception: E)
      extends BeMatcher[Either[Throwable, _]] {
    val expectedExceptionType = exception.getClass.getName
    val expectedMessage = exception.getMessage
    def apply(e: Either[Throwable, _]) =
      e match {
        case l @ Left(e: E) if e.getMessage == expectedMessage =>
          MatchResult(true,
                      s"$l was $expectedExceptionType($expectedMessage)",
                      s"$l was not $expectedExceptionType($expectedMessage)")
        case other =>
          MatchResult(
            false,
            s"Expected Left($expectedExceptionType($expectedMessage)), but got $other",
            s"Expected something that WASN'T Left($expectedExceptionType($expectedMessage)), but that's what we got"
          )
      }
  }

  def leftException(exception: Exception) = new LeftExceptionMatcher(exception)

  def nomadHeaderException(message: String) =
    leftException(new NoSuchElementException(message))

  val nomadErrorException = leftException(
    new RuntimeException("Got error response from Nomad: error"))
}
