load("//tools:modify_rules_scala_merge_jar_phase.bzl", "add_phase_merge_in_correct_order")

# TODO: it would be awfully nice to go through this and throw out as much as possible

load("@io_bazel_rules_scala//scala:scala_toolchain.bzl", "scala_toolchain")
load("@io_bazel_rules_scala//scala:providers.bzl", "declare_deps_provider")
load("//tools:platforms.bzl", "generate_constraints")

generate_constraints()

package(
    default_visibility = ["//visibility:public"],
)

py_binary(
    name = "construct_rpm_spec",
    srcs = ["construct_rpm_spec.py"],
    deps = [],
)

py_binary(
    name = "annotate_images",
    srcs = ["annotate_images.py"],
    deps = [],
)

py_binary(
    name = "build_structured_tar",
    srcs = ["build_structured_tar.py"],
    deps = [],
)

py_binary(
    name = "convert_tar_to_msi",
    srcs = ["convert_tar_to_msi.py"],
    deps = [],
)

py_binary(
    name = "make_rpm_package",
    srcs = ["make_rpm_package.py"],
    deps = [],
)

add_phase_merge_in_correct_order(
    name = "patch_phase_merge",
    visibility = ["//visibility:public"],
)

py_binary(
    name = "repack_tar",
    srcs = ["repack_tar.py"],
    python_version = "PY3",
    deps = [],
)

scala_toolchain(
    name = "my_toolchain_impl",
    dep_providers = [
        ":my_scala_compile_classpath_provider",
        ":my_scala_library_classpath_provider",
        ":my_scala_macro_classpath_provider",
        ":my_scala_xml_provider",
        ":my_parser_combinators_provider",
    ],
    dependency_mode = "direct",
    dependency_tracking_method = "ast",
    scalacopts = [
        "-Ywarn-unused",
        "-language:postfixOps",
    ],
    strict_deps_mode = "error",
    unused_dependency_checker_mode = "error",
    visibility = ["//visibility:public"],
)

toolchain(
    name = "my_scala_toolchain",
    toolchain = "my_toolchain_impl",
    toolchain_type = "@io_bazel_rules_scala//scala:toolchain_type",
    visibility = ["//visibility:public"],
)

declare_deps_provider(
    name = "my_scala_compile_classpath_provider",
    deps_id = "scala_compile_classpath",
    visibility = ["//visibility:public"],
    deps = [
        "@io_bazel_rules_scala_scala_compiler",
        "@io_bazel_rules_scala_scala_library",
        "@io_bazel_rules_scala_scala_reflect",
    ],
)

declare_deps_provider(
    name = "my_scala_library_classpath_provider",
    deps_id = "scala_library_classpath",
    deps = [
        "@io_bazel_rules_scala_scala_library",
        "@io_bazel_rules_scala_scala_reflect",
    ],
)

declare_deps_provider(
    name = "my_scala_macro_classpath_provider",
    deps_id = "scala_macro_classpath",
    deps = [
        "@io_bazel_rules_scala_scala_library",
        "@io_bazel_rules_scala_scala_reflect",
    ],
)

declare_deps_provider(
    name = "my_scala_xml_provider",
    deps_id = "scala_xml",
    deps = ["@io_bazel_rules_scala_scala_xml"],
)

declare_deps_provider(
    name = "my_parser_combinators_provider",
    deps_id = "parser_combinators",
    deps = ["@io_bazel_rules_scala_scala_parser_combinators"],
)

#genrule(
#    name = "pushable-terraform-data",
#    srcs = ["//timberland/jvm:terraform-full-deploy"],
#    outs = ["modules.tar.gz"],
#    cmd = "$(location repack_tar) $(location //timberland/jvm:terraform-full-deploy) $@ opt/radix/timberland/terraform/modules",
#    tools = [":repack_tar"],
#)
