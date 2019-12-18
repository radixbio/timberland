load(
    "@io_bazel_rules_scala//scala:scala.bzl",
    "scala_binary",
    "scala_library",
    #    "scala_library_suite",
    "scala_macro_library",
    "scala_test",
)

package(
    default_visibility = ["//timberland:__subpackages__"],
)

scala_library(
    name = "timberland-lib",
    srcs = glob([
        "src/**/*.scala",
    ]),
    resources = glob(["src/main/resources/**/*"]),
    exports = [
        "//3rdparty/jvm/com/github/xuwei_k:optparse_applicative",
        "//3rdparty/jvm/com/lihaoyi:ammonite_ops",
        "//3rdparty/jvm/com/outr:scribe",
        "//3rdparty/jvm/org/scala_lang/modules:scala_parser_combinators",
        "//shared/shared/shared/shared:shared-shared",
        "//third-party/helm:helm-jvm",
    ],
    deps = [
        "//3rdparty/jvm/com/github/xuwei_k:optparse_applicative",
        "//3rdparty/jvm/com/lihaoyi:ammonite_ops",
        "//3rdparty/jvm/com/outr:scribe",
        "//3rdparty/jvm/org/scala_lang/modules:scala_parser_combinators",
        "//shared/shared/shared/shared:shared-shared",
        "//third-party/helm:helm-jvm",
    ],
)

scala_binary(
    name = "timberland-bin",
    srcs = ["src/main/scala/com/radix/timberland/runner.scala"],
    main_class = "com.radix.timberland.runner",
    resources = glob(["src/main/resources/**/*"]),
    deps = [":timberland-lib"],
)
