load("//tools:modify_rules_scala_merge_jar_phase.bzl", "add_phase_merge_in_correct_order")

# TODO: it would be awfully nice to go through this and throw out as much as possible

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

#genrule(
#    name = "pushable-terraform-data",
#    srcs = ["//timberland/jvm:terraform-full-deploy"],
#    outs = ["modules.tar.gz"],
#    cmd = "$(location repack_tar) $(location //timberland/jvm:terraform-full-deploy) $@ opt/radix/timberland/terraform/modules",
#    tools = [":repack_tar"],
#)
