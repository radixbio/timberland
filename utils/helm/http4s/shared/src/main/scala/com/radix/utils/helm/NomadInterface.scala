package com.radix.utils.helm

//import java.util.UUID

//import cats.data.NonEmptyList

//import scala.concurrent.duration._

//import cats.effect.Effect
import com.radix.utils.helm.NomadHCL._
import com.radix.utils.helm.NomadHCL.defs._
import com.radix.utils.helm.NomadHCL.syntax._

import cats.~>

trait NomadInterface[F[_]] extends (NomadOp ~> F) {

  val URL: String

  val accessToken: Option[String] = None
  val credentials: Option[(String, String)] = None

  def apply[A](op: NomadOp[A]): F[A]

  def nomadCreateJobFromHCL(
    job: JobShim,
    enforceIndex: Boolean = false,
    jobModifyIndex: Int = 0,
    policyOverride: Boolean = false
  ): F[QueryResponse[List[NomadCreateJobResponse]]]

  def nomadListJobs(
    namespace: String = "",
    index: Option[Long] = None,
    wait: Option[Interval] = None
  ): F[List[NomadListJobsResponse]]

  def nomadStopJob(job: String, purge: Boolean = true): F[NomadStopJobResponse]

  def nomadListAllocations(): F[NomadListAllocationsResponse]

  def nomadDescribeAllocation(id: String): F[NomadAllocationResponse]
}

/** A nice place to store the Nomad response headers so we can pass them around */
case class NomadHeaders(
  index: Long,
  lastContact: Long,
  knownLeader: Boolean
)
