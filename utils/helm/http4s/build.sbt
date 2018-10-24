import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}
import com.verizon.helm.http4s.Dependencies
val http4sOrg = "org.http4s"
val http4sVersion = "0.18.11"
val dockeritVersion = "0.9.0"

val scalaTestVersion = "3.0.5"
val scalaCheckVersion = "1.13.4"
lazy val http4s = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .withoutSuffixFor(JVMPlatform)
  .jsSettings(
    Dependencies.jsLibraryDependencies
  )
  .jvmSettings(
    Dependencies.jvmLibraryDependencies
  )
  .in(file("."))

(initialCommands in console) := """
import helm._
import http4s._

import cats.effect.IO
import scala.concurrent.duration.{DurationInt,Duration}

import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.client.blaze.{BlazeClientConfig, PooledHttp1Client}
import org.http4s.headers.{AgentProduct, `User-Agent`}
import org.http4s.util.threads
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.{Executors, ExecutorService}
import javax.net.ssl.SSLContext

def clientEC() = {
  val maxThreads = math.max(4, (Runtime.getRuntime.availableProcessors * 1.5).ceil.toInt)
  val threadFactory = threads.threadFactory(name = (i => s"http4s-blaze-client-$i"), daemon = true)
  Executors.newFixedThreadPool(maxThreads, threadFactory)
}

val config = BlazeClientConfig.defaultConfig.copy(
  idleTimeout = 60.seconds,
  requestTimeout = 3.seconds,
  bufferSize = 8 * 1024,
  userAgent = Some(`User-Agent`(AgentProduct("http4s-blaze", Some(org.http4s.BuildInfo.version))))
)

val http = PooledHttp1Client[IO](maxTotalConnections = 10, config = config)
val c = new Http4sConsulClient(Uri.uri("http://localhost:8500"), http)

"""
