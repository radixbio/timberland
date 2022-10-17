load("@io_bazel_rules_scala//scala:scala.bzl", "scala_library")

scala_library(
    name = "helm-jvm",
    visibility = ["//visibility:public"],
    exports = ["//utils/helm/http4s:helm-http4s"],
    deps = ["//utils/helm/http4s:helm-http4s"],
    scalac_jvm_flags = ["-Xss256m"],
)
