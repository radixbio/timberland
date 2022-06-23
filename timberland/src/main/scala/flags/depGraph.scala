package com.radix.timberland.flags

import cats.effect.{ContextShift, IO}
import cats.implicits._
import scalax.collection.Graph
import scalax.collection.GraphEdge.DiEdge
import scalax.collection.GraphPredef.EdgeAssoc

import scala.concurrent.ExecutionContext

case object depGraph {
  private val ec = ExecutionContext.global
  implicit private val cs: ContextShift[IO] = IO.contextShift(ec)

  def getTransitiveDeps(modules: Set[String]): IO[Set[String]] = buildDepGraph.map { g =>
    modules.flatMap { module =>
      g.nodes.find(module).get.innerNodeTraverser.map(_.value).toSet
    }
  }

  private def buildDepGraph: IO[Graph[String, DiEdge]] = for {
    modules <- tfParser.getModuleList
    depPairs <- modules
      .map(mod => tfParser.getHclDeps(mod).map(mod -> _))
      .parSequence
    graphEdges = depPairs.flatMap {
      case (module, deps) => deps.map(module ~> _)
    }
  } yield Graph.from(modules, graphEdges)
}
