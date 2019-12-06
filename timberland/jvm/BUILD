load(
    "@io_bazel_rules_scala//scala:scala.bzl",
    "scala_binary",
    "scala_library",
    "scala_library_suite",
    "scala_macro_library",
    "scala_test",
)

scala_library(
    name = "timberland-lib",
    srcs = glob([
        "src/**/*.scala",
    ]),
    resources = glob(["src/main/resources/**/*.*"]),
    exports = [
        "//3rdparty/jvm/com/github/xuwei_k:optparse_applicative",
        "//3rdparty/jvm/com/lihaoyi:ammonite_ops",
        "//3rdparty/jvm/com/outr:scribe",
        "//3rdparty/jvm/com/slamdata:matryoshka_core",
        "//3rdparty/jvm/io/circe:circe_core",
        "//3rdparty/jvm/org/scala_lang/modules:scala_parser_combinators",
        "//3rdparty/jvm/org/scalaz:scalaz_core",
        "//3rdparty/jvm/org/typelevel:cats_core",
        "//3rdparty/jvm/org/typelevel:cats_effect",
        "//shared/shared/shared/shared:shared-shared",
        "//third-party/helm:helm-jvm",
    ],
    deps = [
        "//3rdparty/jvm/com/github/xuwei_k:optparse_applicative",
        "//3rdparty/jvm/com/lihaoyi:ammonite_ops",
        "//3rdparty/jvm/com/outr:scribe",
        "//3rdparty/jvm/com/slamdata:matryoshka_core",
        "//3rdparty/jvm/io/circe:circe_core",
        "//3rdparty/jvm/io/circe:circe_parser",
        "//3rdparty/jvm/org/scala_lang/modules:scala_parser_combinators",
        "//3rdparty/jvm/org/scalaz:scalaz_core",
        "//3rdparty/jvm/org/typelevel:cats_core",
        "//3rdparty/jvm/org/typelevel:cats_effect",
        "//shared/shared/shared/shared:shared-shared",
        "//third-party/helm:helm-jvm",
        "//utils/prism/shared:prism-shared",
    ],
)

scala_binary(
    name = "timberland-bin",
    srcs = ["src/main/scala/com/radix/timberland/runner.scala"],
    main_class = "com.radix.timberland.runner",
    resources = glob(["src/main/resources/**/*.*"]),
    deps = [":timberland-lib"],
)
