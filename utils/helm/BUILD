load(
    "@io_bazel_rules_scala//scala:scala.bzl",
    "scala_binary",
    "scala_library",
    "scala_macro_library",
    "scala_test",
)

scala_library(
    name = "helm-jvm",
    visibility = ["//visibility:public"],
    exports = ["//utils/helm/http4s/jvm:helm-http4s"],
    deps = ["//utils/helm/http4s/jvm:helm-http4s"],
)
