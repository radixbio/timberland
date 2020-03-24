load(
    "@io_bazel_rules_scala//scala:scala.bzl",
    "scala_binary",
    "scala_library",
    #    "scala_library_suite",
    "scala_macro_library",
    "scala_test",
)
load("@rules_pkg//:pkg.bzl", "pkg_deb", "pkg_tar")

package(
    default_visibility = ["//timberland:__subpackages__"],
)

scala_library(
    name = "timberland-dependencies",
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

#TODO @sibblegp make this go away
#it's shared dependencies that timberland the commandline tool
#has with the in-container process
timberland_core_srcs = [
    "src/main/scala/com/radix/timberland/defns.scala",
    "src/main/scala/com/radix/timberland/launch/consulutil.scala",
    "src/main/scala/com/radix/timberland/runtime/install.scala",
    "src/main/scala/com/radix/timberland/runtime/services.scala",
]

timberland_launcher_srcs = [
    "src/main/scala/com/radix/timberland/launch/kafka.scala",
    "src/main/scala/com/radix/timberland/launch/zookeeper.scala",
    "src/main/scala/com/radix/timberland/launcher.scala",
    "src/main/scala/com/radix/timberland/launch/yugabyte.scala",
]

scala_library(
    name = "timberland-core-lib",
    srcs = timberland_core_srcs,
    resources = glob([
        "src/main/resources/consul/**/*",
        "src/main/resources/nomad/**/*",
        "src/main/resources/systemd/**/*",
        "src/main/resources/application.conf",
    ]),
    exports = [":timberland-dependencies"],
    deps = [":timberland-dependencies"],
)

scala_library(
    name = "timberland-lib",
    # Exclude the kafka and zookeeper sources
    srcs = glob(
        [
            "src/**/*.scala",
        ],
        exclude = timberland_launcher_srcs + timberland_core_srcs,
    ),
    exports = [
        ":timberland-core-lib",
        "//3rdparty/jvm/com/github/xuwei_k:optparse_applicative",
        "//3rdparty/jvm/com/lihaoyi:ammonite_ops",
        "//3rdparty/jvm/com/outr:scribe",
        "//3rdparty/jvm/org/scala_lang/modules:scala_parser_combinators",
        "//shared/shared/shared/shared:shared-shared",
        "//third-party/helm:helm-jvm",
        "//third-party/helm/core/shared:helm-elemental",
        "//utils/sheets/jvm/src/main/scala/com/radix/util/sheets:sheets-interps",  #TODO remove this
    ],
    deps = [
        ":timberland-core-lib",
        "//third-party/helm/core/shared:helm-elemental",
        "//utils/sheets/jvm/src/main/scala/com/radix/util/sheets:sheets-interps",  #TODO remove this
    ],
)

scala_library(
    name = "timberland-launcher-lib",
    srcs = timberland_launcher_srcs,
    exports = [
        ":timberland-core-lib",
    ],
    deps = [
        ":timberland-core-lib",
    ],
)

scala_binary(
    name = "timberland-bin",
    srcs = ["src/main/scala/com/radix/timberland/runner.scala"],
    main_class = "com.radix.timberland.runner",
    deps = [":timberland-lib"],
)

scala_binary(
    name = "timberland-launcher",
    main_class = "com.radix.timberland.launcher",
    deps = [
        ":timberland-launcher-lib",
    ],
)

pkg_tar(
    name = "timberland-executables",
    srcs = [
        ":timberland-bin",
        ":timberland-bin_deploy.jar",
    ],
    package_dir = "/opt/radix",
    #        package_dir = "/tmp/test1",
    strip_prefix = "/",
    deps = ["//device_drivers/hw_discovery/jvm:disco-deploy"],
)

pkg_tar(
    name = "timberland-exec",
    srcs = [
        "wrappers/timberland",
        "//timberland/jvm:timberland-bin_deploy.jar",
        "//timberland/jvm:timberland-launcher_deploy.jar",
    ],
    package_dir = "/opt/radix/timberland/exec",
)

pkg_deb(
    name = "timberland-deb",
    data = ":timberland-exec",
    depends = [
        "docker.io | docker-ce",
        "openjdk-8-jdk",
    ],
    description = "Radix Timberland deploy file",
    maintainer = "George Sibble",
    package = "radix-timberland",
    version = "0.1",
)

sh_test(
    name = "timberland-ci",
    size = "large",
    srcs = [
        "src/test/ci.sh",
    ],
    data = [
        "src/test/service_test.py",
        ":radix-timberland_0.1_all.deb",
    ],
    #    local = True,
    tags = ["no-sandbox"],
)
