package com.radix.timberland

import java.io.File
import java.net.{InetAddress, URL, NetworkInterface}

//import ammonite._
//import ammonite.ops._
import cats._
import cats.data._
import cats.effect.{Effect}
import cats.implicits._
import com.radix.utils.helm.ServiceResponse
import io.circe.Json
import scala.concurrent.duration._
import sun.misc.{Signal, SignalHandler}
import scala.collection.JavaConverters._

package object radixdefs {

  /**
    * This trait allows the use of a SHA256SUMS like file to dereference a binary filtering by the predicates
    * @tparam F The effect type
    */
  trait SHAVTableDownloader[F[_]] {
    type T

    /**
      * This is the method to override to allow vtable-like lookups using a SHA256SUMS file that meets some predicates.
      * @param downloadURL The URL containing the files to download
      * @param shavtable the URL containing the SHA256SUMS file
      * @param predicates the predicates to filter to allow a unique single element to be looked up
      * @return the file to be downloaded, and what type of file it is.
      */
    def get(downloadURL: URL, shavtable: URL, predicates: List[String]): F[(File, T)]
  }

  sealed trait SHAArtifactRetrival
  case object Consul extends SHAArtifactRetrival
  case object Nomad  extends SHAArtifactRetrival

  /**
    * A purely functional copy trait to move between path-like identifiers
    * @tparam F the effect type
    */
  trait FunctionalCopy[F[_]] {

    /**
      * the functional copy method to copy from `from` to `to`
      * @param from the path to copy from
      * @param to the path to copy to
      * @return The side effect wrapped in the `F` effect.
      */
    def fncopy(from: os.Path, to: os.Path): F[Unit]
  }

  /**
  * Factoring out the interface code [from `RuntimeServicesAlg`] allows it to be used in many places
    * @tparam F The effect type
    */
  trait LocalEthInfoAlg[F[_]] {
    def getNetworkInterfaces: F[List[String]]
  }

  class NetworkInfoExec[F[_]](implicit F: Effect[F]) extends LocalEthInfoAlg[F] {
    override def getNetworkInterfaces: F[List[String]] = F.delay {
      NetworkInterface.getNetworkInterfaces.asScala.toList
        .flatMap(_.getInetAddresses.asScala)
        .filter(_.getAddress.length == 4) // only ipv4
        .map(_.getHostAddress)
        .distinct
    }
  }

  /**
    * This trait serves as an interface to bring up the core services
    * @tparam F
    */
  trait RuntimeServicesAlg[F[_]] extends LocalEthInfoAlg[F] {
    def searchForPort(netinf: List[String], port: Int): F[Option[NonEmptyList[String]]]
    def readConfig(wd: os.Path, fname: String): F[String]
    def parseJson(json: String): F[Json]
    def mkTempFile(contents: String, fname: String, exn: String = "json"): F[os.Path]
    def startConsul(bind_addr: String, consulSeedsO: Option[String], bootstrapExpect: Int): F[Unit]
    def startNomad(bind_addr: String, bootstrapExpect: Int): F[Unit]
    def startWeave(hosts: List[String]): F[Unit]
  }
}
