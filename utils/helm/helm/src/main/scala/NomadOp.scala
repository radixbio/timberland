package com.radix.utils.helm

import cats.free.Free
import cats.free.Free.liftF
import com.radix.utils.helm.NomadHCL._
import com.radix.utils.helm.NomadHCL.defs._
import com.radix.utils.helm.NomadHCL.syntax._

sealed abstract class NomadOp[A] extends Product with Serializable

object NomadOp {

  final case class NomadCreateJobFromHCL(
    job: JobShim,
    enforceIndex: Boolean = false,
    jobModifyIndex: Int = 0,
    policyOverride: Boolean = false,
  ) extends NomadOp[QueryResponse[List[NomadCreateJobResponse]]]

  final case class NomadStopJob(
    job: String,
    purge: Boolean = true,
  ) extends NomadOp[NomadStopJobResponse]

  final case class NomadListJobs(
    namespace: String = "",
    index: Option[Long] = None,
    maxWait: Option[Interval] = None,
  ) extends NomadOp[List[NomadListJobsResponse]]

  final case class NomadReadRaftConfiguration(maxWait: Option[Interval])
      extends NomadOp[NomadReadRaftConfigurationResponse]

  final case class NomadListAllocations() extends NomadOp[NomadListAllocationsResponse]

  type NomadOpF[A] = Free[NomadOp, A]
  def nomadCreateJobFromHCL(
    job: JobShim,
    enforceIndex: Boolean = false,
    jobModifyIndex: Int = 0,
    policyOverride: Boolean = false,
  ): NomadOpF[QueryResponse[List[NomadCreateJobResponse]]] =
    liftF(NomadCreateJobFromHCL(job, enforceIndex, jobModifyIndex, policyOverride))

  def nomadListJobs(
    namespace: String = "",
    index: Option[Long] = None,
    maxWait: Option[Interval] = None,
  ): NomadOpF[List[NomadListJobsResponse]] =
    liftF(NomadListJobs(namespace, index, maxWait))

  def nomadStopJob(job: String, purge: Boolean = true): NomadOpF[NomadStopJobResponse] = liftF(NomadStopJob(job, purge))

  def nomadReadRaftConfiguration(
    maxWait: Option[Interval] = None
  ): NomadOpF[NomadReadRaftConfigurationResponse] = liftF(NomadReadRaftConfiguration(maxWait))

  def nomadListAllocations(): NomadOpF[NomadListAllocationsResponse] = liftF(NomadListAllocations())
}
