package com.radix.utils.helm

import io.circe.Decoder.Result
import io.circe._

final case class NomadListJobsResponse(
  id: String,
  parentID: String,
  name: String,
  datacenters: List[String],
  jobType: String,
  priority: Int,
  periodic: Boolean,
  parameterizedJob: Boolean,
  stop: Boolean,
  status: String,
  statusDescription: String,
  jobSummary: NomadJobSummary,
  createIndex: Long,
  modifyIndex: Long,
  jobModifyIndex: Long,
  submitTime: Long
)

object NomadListJobsResponse {
  implicit def NomadListJobsResponseDecoder: Decoder[NomadListJobsResponse] = new Decoder[NomadListJobsResponse] {
    final def apply(j: HCursor): Decoder.Result[NomadListJobsResponse] = {
      for {
        id <- j.downField("ID").as[String]
        parentID <- j.downField("ParentID").as[String]
        name <- j.downField("Name").as[String]
        datacenters <- j.downField("Datacenters").as[List[String]]
        jobType <- j.downField("Type").as[String]
        priority <- j.downField("Priority").as[Int]
        periodic <- j.downField("Periodic").as[Boolean]
        parameterizedJob <- j.downField("ParameterizedJob").as[Boolean]
        stop <- j.downField("Stop").as[Boolean]
        status <- j.downField("Status").as[String]
        statusDescription <- j.downField("StatusDescription").as[String]
        jobSummary <- j.downField("JobSummary").as[NomadJobSummary]
        createIndex <- j.downField("CreateIndex").as[Long]
        modifyIndex <- j.downField("ModifyIndex").as[Long]
        jobModifyIndex <- j.downField("JobModifyIndex").as[Long]
        submitTime <- j.downField("SubmitTime").as[Long]
      } yield NomadListJobsResponse(
        id,
        parentID,
        name,
        datacenters,
        jobType,
        priority,
        periodic,
        parameterizedJob,
        stop,
        status,
        statusDescription,
        jobSummary,
        createIndex,
        modifyIndex,
        jobModifyIndex,
        submitTime
      )
    }
  }
}

final case class NomadJobSummary(
  jobId: String,
  namespace: String,
  summary: Map[String, NomadTaskGroupSummary],
  children: NomadJobSummaryChildren,
  createIndex: Long,
  modifyIndex: Long
)

object NomadJobSummary {
  implicit def NomadJobSummaryDecoder: Decoder[NomadJobSummary] = new Decoder[NomadJobSummary] {
    final def apply(j: HCursor): Decoder.Result[NomadJobSummary] = {
      for {
        jobId <- j.downField("JobID").as[String]
        namespace <- j.downField("Namespace").as[String]
        summary <- j.downField("Summary").as[Map[String, NomadTaskGroupSummary]]
        children <- j.downField("Children").as[NomadJobSummaryChildren]
        createIndex <- j.downField("CreateIndex").as[Long]
        modifyIndex <- j.downField("ModifyIndex").as[Long]
      } yield NomadJobSummary(
        jobId,
        namespace,
        summary,
        children,
        createIndex,
        modifyIndex
      )
    }
  }
}

final case class NomadTaskGroupSummary(
  queued: Long,
  complete: Long,
  failed: Long,
  running: Long,
  starting: Long,
  lost: Long
)

object NomadTaskGroupSummary {
  implicit def NomadTaskGroupSummaryDecoder: Decoder[NomadTaskGroupSummary] = new Decoder[NomadTaskGroupSummary] {
    override def apply(j: HCursor): Result[NomadTaskGroupSummary] = {
      for {
        queued <- j.downField("Queued").as[Long]
        complete <- j.downField("Complete").as[Long]
        failed <- j.downField("Failed").as[Long]
        running <- j.downField("Running").as[Long]
        starting <- j.downField("Starting").as[Long]
        lost <- j.downField("Lost").as[Long]
      } yield NomadTaskGroupSummary(queued, complete, failed, running, starting, lost)
    }
  }
}

final case class NomadJobSummaryChildren(pending: Long, running: Long, dead: Long)

object NomadJobSummaryChildren {
  implicit def NomadJobSummaryChildrenDecoder: Decoder[NomadJobSummaryChildren] = new Decoder[NomadJobSummaryChildren] {
    override def apply(j: HCursor): Result[NomadJobSummaryChildren] = {
      for {
        pending <- j.downField("Pending").as[Long]
        running <- j.downField("Running").as[Long]
        dead <- j.downField("Dead").as[Long]
      } yield NomadJobSummaryChildren(pending, running, dead)
    }
  }
}
