package(
    default_visibility = ["//utils/helm:__subpackages__"],
)

scala_library(
    name = "helm-json",
    srcs = glob([
        "src/main/scala/*Response.scala",
        "src/main/scala/*Result.scala",
        "src/main/scala/*Status.scala",
        "src/main/scala/*Parameter.scala",
    ]) + [
        "src/main/scala/Interval.scala",
    ],
    exports = [
        "@third_party//3rdparty/jvm/io/circe:circe_core",
        "@third_party//3rdparty/jvm/io/circe:circe_fs2",
        "@third_party//3rdparty/jvm/io/circe:circe_generic",
        "@third_party//3rdparty/jvm/io/circe:circe_parser",
        "@third_party//3rdparty/jvm/org/http4s:http4s_blaze_client",
        "@third_party//3rdparty/jvm/org/http4s:http4s_circe",
        "@third_party//3rdparty/jvm/org/http4s:http4s_core",
    ],
    deps = [
        "@third_party//3rdparty/jvm/io/circe:circe_core",
        "@third_party//3rdparty/jvm/io/circe:circe_fs2",
        "@third_party//3rdparty/jvm/io/circe:circe_generic",
        "@third_party//3rdparty/jvm/io/circe:circe_parser",
        "@third_party//3rdparty/jvm/org/http4s:http4s_blaze_client",
        "@third_party//3rdparty/jvm/org/http4s:http4s_circe",
        "@third_party//3rdparty/jvm/org/http4s:http4s_core",
    ],
)

scala_library(
    name = "helm-hcl",
    srcs = [
        "src/main/scala/NomadHCL.scala",
    ],
    exports = [
        "@third_party//3rdparty/jvm/com/chuusai:shapeless",
        "@third_party//3rdparty/jvm/org/typelevel:cats_core",
    ],
    deps = [
        "@third_party//3rdparty/jvm/com/chuusai:shapeless",
        "@third_party//3rdparty/jvm/org/typelevel:cats_core",
    ],
)

scala_library(
    name = "helm-core-lib",
    srcs = [
        "src/main/scala/HelmOp.scala",
        "src/main/scala/NomadOp.scala",
        "src/main/scala/VaultOps.scala",
        "src/main/scala/package.scala",
    ],
    visibility = ["//visibility:public"],
    exports = [
        ":helm-hcl",
        ":helm-json",
        "@third_party//3rdparty/jvm/org/typelevel:cats_free",
    ],
    deps = [
        ":helm-hcl",
        ":helm-json",
        "@third_party//3rdparty/jvm/io/circe:circe_core",
        "@third_party//3rdparty/jvm/io/circe:circe_generic_extras",
        "@third_party//3rdparty/jvm/org/typelevel:cats_free",
    ],
)

scala_library(
    name = "helm-elemental",
    srcs = [
        "src/main/scala/ElementalOps.scala",
        "src/main/scala/VaultOps.scala",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//utils/helm/http4s:helm-http4s",
        "@third_party//3rdparty/jvm/io/circe:circe_core",
        "@third_party//3rdparty/jvm/io/circe:circe_generic_extras",
        "@third_party//3rdparty/jvm/org/typelevel:cats_core",
        "@third_party//3rdparty/jvm/org/typelevel:cats_effect",
    ],
)

scala_library(
     name = "helm-interfaces",
     srcs = [
         "src/main/scala/ConsulInterface.scala",
         "src/main/scala/NomadInterface.scala",
         "src/main/scala/VaultInterface.scala"
     ],
     visibility = ["//utils/helm:__subpackages__"],
     exports = [
         ":helm-core-lib"
     ],
     deps = [
         ":helm-core-lib",
     ],
 )



