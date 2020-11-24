load("//tools:modify_rules_scala_merge_jar_phase.bzl", "add_phase_merge_in_correct_order")

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

genrule(
    name = "pushable-terraform-data",
    srcs = ["//timberland/jvm:terraform-full-deploy"],
    outs = ["modules.tar.gz"],
    cmd = "$(location repack_tar) $(location //timberland/jvm:terraform-full-deploy) $@ opt/radix/timberland/terraform/modules",
    tools = [":repack_tar"],
)

genrule(
    name = "gen-cloud-upload-config",
    srcs = [
        "//timberland/jvm:git-branch",
        "@//:git-head-file",
    ],
    outs = ["cloud_upload_config.sh"],
    cmd = """BN=`cat $(location //timberland/jvm:git-branch)`
if [ -z "$${BN:-}" ]; then
    BN=`cat $(location @//:git-head-file) | sed -e 's/_/-/g' | sed -e 's/\//-/g'`
    BN=$${BN#"ref: refs-heads-"}
fi
echo "ORG_NAME=\"radixlabs\"" >> $@
echo "MODULE=\"$$BN\"" >> $@
echo "PROVIDER=\"local\"" >> $@
echo "API_TOKEN=\"N04sm2kOWPs4qA.atlasv1.diF3k3zgB6zHivDtTizrhJtQTEmg69NZqtGaZ6F20KpreWQWNLIp1UhViPa9NK2LUfM\"" >> $@
""",
)
