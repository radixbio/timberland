load("@io_bazel_rules_scala//scala:advanced_usage/scala.bzl", "make_scala_binary", "make_scala_library", "make_scala_test")

#load("@io_bazel_rules_scala//scala/scalafmt:phase_scalafmt_ext.bzl", "ext_scalafmt")
load(
    "@io_bazel_rules_scala//scala:advanced_usage/providers.bzl",
    _ScalaRulePhase = "ScalaRulePhase",
)
load(
    "@io_bazel_rules_scala//scala/private:phases/phases.bzl",
    _phase_scalafmt = "phase_scalafmt",
)

ext_scalafmt = {
    "attrs": {
        "config": attr.label(
            allow_single_file = [".conf"],
            default = "@scalafmt_default//:config",
            doc = "The Scalafmt configuration file.",
        ),
        "format": attr.bool(
            default = True,
            doc = "Switch of enabling formatting.",
        ),
        "_fmt": attr.label(
            cfg = "host",
            default = "@io_bazel_rules_scala//scala/scalafmt",
            executable = True,
        ),
        "_runner": attr.label(
            allow_single_file = True,
            default = "@io_bazel_rules_scala//scala/scalafmt:runner",
        ),
        "_testrunner": attr.label(
            allow_single_file = True,
            default = "@io_bazel_rules_scala//scala/scalafmt:testrunner",
        ),
    },
    "outputs": {
        "scalafmt_runner": "%{name}.format",
        "scalafmt_testrunner": "%{name}.format-test",
    },
    "phase_providers": [
        "@io_bazel_rules_scala//scala/scalafmt:phase_scalafmt",
    ],
}

def _scalafmt_singleton_implementation(ctx):
    return [
        _ScalaRulePhase(
            custom_phases = [
                ("$", "", "scalafmt", _phase_scalafmt),
            ],
        ),
    ]

scalafmt_singleton = rule(
    implementation = _scalafmt_singleton_implementation,
)

scala_binary = make_scala_binary(ext_scalafmt)
scala_library = make_scala_library(ext_scalafmt)
scala_test = make_scala_test(ext_scalafmt)
