package com.radix.utils.helm.http4s

import cats.effect.Effect
import cats.implicits._
import com.radix.utils.helm._
import org.http4s.Method.POST
//import journal.Logger
import logstage._
import org.http4s._
import org.http4s.client._
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers._
import org.http4s.circe._
import com.radix.utils.helm.NomadHCL._
import com.radix.utils.helm.NomadHCL.defs._
import com.radix.utils.helm.NomadHCL.syntax._

import scala.collection.Map
import cats.effect.Sync
import io.circe._
import io.circe.syntax._
import org.http4s.Method.POST
import org.http4s.circe._
import org.http4s.client.Client

final class Http4sNomadClient[F[_]](
  val baseUri: Uri,
  val client: Client[F],
  override val accessToken: Option[String] = util.getTokenFromEnvVars(),
  override val credentials: Option[(String, String)] = None
)(implicit F: Effect[F])
    extends NomadInterface[F] {

  override val URL: String = baseUri.renderString

  private[this] val dsl = new Http4sClientDsl[F] {}
  import dsl._

  private implicit val nomadCreateJobResponseDecoder: EntityDecoder[F, NomadCreateJobResponse] =
    jsonOf[F, NomadCreateJobResponse]

  private implicit val listNomadListJobsDecoder: EntityDecoder[F, List[NomadListJobsResponse]] =
    jsonOf[F, List[NomadListJobsResponse]]

  private implicit val nomadReadRaftConfigurationDecoder: EntityDecoder[F, NomadReadRaftConfigurationResponse] =
    jsonOf[F, NomadReadRaftConfigurationResponse]

  private implicit val nomadStopJobResponseDecoder: EntityDecoder[F, NomadStopJobResponse] =
    jsonOf[F, NomadStopJobResponse]

  private implicit val nomadListAllocationsResponseDecoder: EntityDecoder[F, NomadListAllocationsResponse] =
    jsonOf[F, NomadListAllocationsResponse]

  private implicit val nomadAllocationResponseDecoder: EntityDecoder[F, NomadAllocationResponse] =
    jsonOf[F, NomadAllocationResponse]

  private val log = IzLogger()

  def apply[A](op: NomadOp[A]): F[A] = op match {
    case NomadOp.NomadCreateJobFromHCL(hcl, enforceIndex, jobModifyIndex, policyOverride) =>
      nomadCreateJobFromHCL(hcl, enforceIndex, jobModifyIndex, policyOverride): F[A]

    case NomadOp.NomadListJobs(prefix, index, wait) =>
      nomadListJobs(prefix, index, wait)
    case NomadOp.NomadReadRaftConfiguration(maxWait) => nomadCReadRaftConfiguration((maxWait))
    case NomadOp.NomadStopJob(job, purge)            => nomadStopJob(job, purge)
    case NomadOp.NomadListAllocations()              => nomadListAllocations()
  }

  private def addNomadToken(req: Request[F]): Request[F] =
    accessToken.fold(req)(tok => req.putHeaders(Header("X-Nomad-Token", tok)))

  private def addCreds(req: Request[F]): Request[F] =
    credentials.fold(req) {
      case (un, pw) => req.putHeaders(Authorization(BasicCredentials(un, pw)))
    }

  private def handleNomadErrorResponse(response: Response[F]): F[Throwable] = {
    response
      .as[String]
      .map(errorMsg => {
        println(response)
        new RuntimeException("Got error response from Nomad: " + errorMsg)
      })
  }
  /*
  def parseHCLString(hcl: String, canonicalize: Boolean = false): F[String] =
    for {
      _ <- F.delay(log.debug(s"parsing nomad HCL create job command $hcl"))
      body = Json("JobHCL" -> jString(hcl),
                  "Canonicalize" -> jBool(canonicalize))
      parseUri = baseUri / "v1" / "jobs" / "parse"

      req = POST(body, parseUri)
        .map(addNomadToken _)
        .map(addCreds _)
      response <- client.expectOr[String](req)(handleNomadErrorResponse)
    } yield {
      log.debug(
        s"nomad parse request for create job hcl $hcl resulted in response $response")
      response
    }

   */

  def parseHCL(job: JobShim, canonicalize: Boolean = false): F[String] =
    for {
      _ <- F.delay(log.debug(s"parsing nomad HCL create job command ${job.name}"))
      // Convert the JobShim object to a string.
      hcl = implicitly[HCLAble[JobShim]].show(job)
      body = Json.obj("JobHCL" -> hcl.asJson, "Canonicalize" -> canonicalize.asJson)
      parseUri = baseUri / "v1" / "jobs" / "parse"

      req = POST(body, parseUri)
        .map(addNomadToken _)
        .map(addCreds _)
      response <- client.expectOr[String](req)(handleNomadErrorResponse)
    } yield {
      log.debug(s"nomad parse request for create job ${job.name} (hcl $hcl) resulted in response $response")
      response
    }

  /*
  def nomadCreateJobFromHCLString(
      hcl: String,
      canonicalize: Boolean = false,
      enforceIndex: Boolean = false,
      jobModifyIndex: Int = 0,
      policyOverride: Boolean = false
  ): F[QueryResponse[List[NomadCreateJobResponse]]] = {

    for {
      _ <- F.delay(log.debug(s"parsing nomad job: $hcl"))
      hclToJsonStr <- parseHCLString(hcl, canonicalize)

      body = s"""{"Job":$hclToJsonStr,"EnforceIndex":$enforceIndex,"JobModifyIndex":$jobModifyIndex,"PolicyOverride":$policyOverride}"""

      jobUri = baseUri / "v1" / "jobs"

      req = POST(body, jobUri)
        .map(addNomadToken _)
        .map(addCreds _)

      response <- client.expectOr[NomadCreateJobResponse](req)(
        handleNomadErrorResponse)
    } yield {
      log.debug(s"nomad response for create job $hcl was $response")
      QueryResponse(List(response),
                    response.index,
                    response.knownLeader,
                    response.lastContact)
    }
  }
   */

  def nomadCreateJobFromHCL(
    job: JobShim,
    enforceIndex: Boolean = false,
    jobModifyIndex: Int = 0,
    policyOverride: Boolean = false
  ): F[QueryResponse[List[NomadCreateJobResponse]]] = {

    for {
      _ <- F.delay(log.debug(s"parsing nomad job: ${job.name}"))
      hclToJsonStr <- parseHCL(job, true)

      body = s"""{"Job":$hclToJsonStr,"EnforceIndex":$enforceIndex,"JobModifyIndex":$jobModifyIndex,"PolicyOverride":$policyOverride}"""

      jobUri = baseUri / "v1" / "jobs"

      req = POST(body, jobUri)
        .map(addNomadToken _)
        .map(addCreds _)

      response <- client.expectOr[NomadCreateJobResponse](req)(handleNomadErrorResponse)
    } yield {
      log.debug(s"nomad response for create job ${job.name} was $response")
      QueryResponse(List(response), response.index, response.knownLeader, response.lastContact)
    }
  }

  def nomadListJobs(
    prefix: String = "",
    index: Option[Long] = None,
    wait: Option[Interval] = None
  ): F[List[NomadListJobsResponse]] = {
    for {
      _ <- F.delay(log.debug(s"listing nomad jobs: $prefix"))

      jobUri = (baseUri / "v1" / "jobs")
        .+?(name = "prefix", prefix)
        .+??("index", index)
        .+??("wait", wait.map(Interval.toString))

      req = Method
        .GET(jobUri)
        .map(addNomadToken _)
        .map(addCreds _)

      response <- client.expectOr[List[NomadListJobsResponse]](req)(handleNomadErrorResponse)

    } yield {
      log.debug(s"nomad response for list jobs $prefix was $response")
      response
    }

  }

  def nomadCReadRaftConfiguration(maxWait: Option[Interval] = None): F[NomadReadRaftConfigurationResponse] = {
    for {
      _ <- F.delay(log.debug("Reading Nomad Raft Configuration"))

      raftConfigUri = (baseUri / "v1" / "operator" / "raft" / "configuration")
        .+??("wait", maxWait.map(Interval.toString))

      req = Method.GET(raftConfigUri).map(addNomadToken _).map(addCreds _)

      response <- client.expectOr[NomadReadRaftConfigurationResponse](req)(handleNomadErrorResponse)
      _ <- F.delay(log.debug(s"****************response: $response"))
    } yield {
      log.debug(s"nomad response for raft config was $response")
      response
    }
  }

  def nomadStopJob(job: String, purge: Boolean = true): F[NomadStopJobResponse] = {
    for {
      _ <- F.delay(log.debug(s"Stopping Nomad Job ${job}"))
      deleteUri = (baseUri / "v1" / "job" / job).+?("purge", purge.toString)
      req = Method.DELETE(deleteUri).map(addNomadToken _).map(addCreds _)

      response <- client.expectOr[NomadStopJobResponse](req)(handleNomadErrorResponse)
      _ <- F.delay(log.debug(s"Delete Response: $response"))
    } yield response
  }

  override def nomadListAllocations(): F[NomadListAllocationsResponse] = {
    val listUri = (baseUri / "v1" / "allocations")
    val req = Method.GET(listUri).map(addNomadToken _).map(addCreds _)
    client.expectOr[NomadListAllocationsResponse](req)(handleNomadErrorResponse)
  }

  override def nomadDescribeAllocation(id: String): F[NomadAllocationResponse] = {
    val uri = baseUri / "v1" / "allocation" / id
    val req = Method.GET(uri).map(addNomadToken _).map(addCreds _)
    client.expectOr[NomadAllocationResponse](req)(handleNomadErrorResponse)
  }

}
