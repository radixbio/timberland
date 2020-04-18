load(
    "@io_bazel_rules_scala//scala:scala.bzl",
    "scala_binary",
    "scala_library",
    "scala_macro_library",
    "scala_test",
)

scala_library(
    name = "helm-interfaces",
    srcs = glob(["src/main/scala/**/*.scala"]),
    visibility = ["//utils/helm:__subpackages__"],
    exports = [
        "//utils/helm/core/shared:helm-core-lib",
    ],
    deps = [
        "//utils/helm/core/shared:helm-core-lib",
    ],
)
