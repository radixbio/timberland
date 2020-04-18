load(
    "@io_bazel_rules_scala//scala:scala.bzl",
    "scala_binary",
    "scala_library",
    "scala_macro_library",
    "scala_test",
)

package(
    default_visibility = ["//utils/helm:__subpackages__"],
)

scala_library(
    name = "helm-http4s",
    srcs = glob(["src/main/scala/**/*.scala"]),
    resources = ["resources/reference.conf"],
    visibility = ["//visibility:public"],
    exports = [
        "//3rdparty/jvm/co/fs2:fs2_core",
        "//3rdparty/jvm/co/fs2:fs2_io",
        "//3rdparty/jvm/co/fs2:fs2_reactive_streams",
        "//3rdparty/jvm/com/outr:scribe",
        "//3rdparty/jvm/io/7mind/izumi:logstage_core",
        "//3rdparty/jvm/io/circe:circe_core",
        "//3rdparty/jvm/io/circe:circe_fs2",
        "//3rdparty/jvm/io/circe:circe_generic",
        "//3rdparty/jvm/org/http4s:http4s_argonaut",
        "//3rdparty/jvm/org/http4s:http4s_blaze_client",
        "//3rdparty/jvm/org/http4s:http4s_circe",
        "//3rdparty/jvm/org/http4s:http4s_core",
        "//3rdparty/jvm/org/typelevel:cats_effect",
        "//utils/helm/core/shared:helm-core-lib",
        "//utils/helm/http4s/shared:helm-interfaces",
    ],
    deps = [
        "//3rdparty/jvm/co/fs2:fs2_core",
        "//3rdparty/jvm/co/fs2:fs2_io",
        "//3rdparty/jvm/co/fs2:fs2_reactive_streams",
        "//3rdparty/jvm/com/outr:scribe",
        "//3rdparty/jvm/io/7mind/izumi:logstage_core",
        "//3rdparty/jvm/io/circe:circe_core",
        "//3rdparty/jvm/io/circe:circe_fs2",
        "//3rdparty/jvm/io/circe:circe_generic",
        "//3rdparty/jvm/org/http4s:http4s_argonaut",
        "//3rdparty/jvm/org/http4s:http4s_blaze_client",
        "//3rdparty/jvm/org/http4s:http4s_circe",
        "//3rdparty/jvm/org/http4s:http4s_core",
        "//3rdparty/jvm/org/typelevel:cats_effect",
        "//utils/helm/core/shared:helm-core-lib",
        "//utils/helm/http4s/shared:helm-interfaces",
    ],
)

scala_test(
    name = "helm-test",
    srcs = glob(
        ["src/test/scala/**/*.scala"],
    ),
    resources = ["src/test/resources/logback-test.xml"],
    deps = [
        ":helm-http4s",
        "//3rdparty/jvm/com/dimafeng:testcontainers_scala",
        "//3rdparty/jvm/org/scalacheck",
        "//3rdparty/jvm/org/slf4j:slf4j_simple",
    ],
)
