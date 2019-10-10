import com.radix.timberland.Dependencies

lazy val helm       = Dependencies.helm dependsOn (helmCore, helmHttp4s) aggregate (helmCore, helmHttp4s)
lazy val helmCore   = Dependencies.helmCore in file("./first-party/helm/core")
lazy val helmHttp4s = Dependencies.helmHttp4s in file("./first-party/helm/http4s") dependsOn (helmCore) aggregate (helmCore)
lazy val prism = Dependencies.prismProject dependsOn shared
lazy val shared = Dependencies.sharedProject

lazy val timberland = Dependencies.timberland.dependsOn(helm, shared, prism).aggregate(helm, shared, prism).in(file("."))

lazy val root = timberland
onLoad in Global ~= (_ andThen ("project timberland" :: _))
