package(
    default_visibility = ["//utils/helm:__subpackages__"],
)

scala_library(
    name = "helm-http4s",
    srcs = glob(["src/main/scala/*.scala"]),
    resources = ["src/main/resources/reference.conf"],
    visibility = ["//visibility:public"],
    exports = [
        "//utils/helm/helm:helm-core-lib",
        "//utils/helm/helm:helm-interfaces",
        "//utils/tls:consul-tls",
        "@third_party//3rdparty/jvm/co/fs2:fs2_core",
        "@third_party//3rdparty/jvm/co/fs2:fs2_io",
        "@third_party//3rdparty/jvm/co/fs2:fs2_reactive_streams",
        "@third_party//3rdparty/jvm/com/outr:scribe",
        "@third_party//3rdparty/jvm/io/7mind/izumi:logstage_core",
        "@third_party//3rdparty/jvm/io/circe:circe_core",
        "@third_party//3rdparty/jvm/io/circe:circe_fs2",
        "@third_party//3rdparty/jvm/io/circe:circe_generic",
        "@third_party//3rdparty/jvm/io/circe:circe_parser",
        "@third_party//3rdparty/jvm/org/http4s:http4s_blaze_client",
        "@third_party//3rdparty/jvm/org/http4s:http4s_circe",
        "@third_party//3rdparty/jvm/org/http4s:http4s_core",
        "@third_party//3rdparty/jvm/org/typelevel:cats_effect",
    ],
    deps = [
        "//utils/helm/helm:helm-core-lib",
        "//utils/helm/helm:helm-interfaces",
        "@third_party//3rdparty/jvm/co/fs2:fs2_core",
        "@third_party//3rdparty/jvm/co/fs2:fs2_io",
        "@third_party//3rdparty/jvm/co/fs2:fs2_reactive_streams",
        "@third_party//3rdparty/jvm/com/outr:scribe",
        "@third_party//3rdparty/jvm/io/7mind/izumi:logstage_core",
        "@third_party//3rdparty/jvm/io/circe:circe_core",
        "@third_party//3rdparty/jvm/io/circe:circe_fs2",
        "@third_party//3rdparty/jvm/io/circe:circe_generic",
        "@third_party//3rdparty/jvm/io/circe:circe_parser",
        "@third_party//3rdparty/jvm/org/http4s:http4s_blaze_client",
        "@third_party//3rdparty/jvm/org/http4s:http4s_circe",
        "@third_party//3rdparty/jvm/org/http4s:http4s_core",
        "@third_party//3rdparty/jvm/org/typelevel:cats_effect",
    ],
)

scala_test(
    name = "helm-test",
    srcs = glob(
        include = ["src/test/scala/*.scala"],
        exclude = [
            "src/test/scala/NomadOpTests.scala",
            "src/test/scala/Integration.scala",
            "src/test/scala/VaultSpec.scala",
        ],
    ),
    resources = ["src/test/resources/logback-test.xml"],
    deps = [
        ":helm-http4s",
        "@third_party//3rdparty/jvm/com/dimafeng:testcontainers_scala",
        "@third_party//3rdparty/jvm/org/scalacheck",
        "@third_party//3rdparty/jvm/org/scalatestplus:scalatestplus_scalacheck",
    ],
)
