load("@rules_pkg//:pkg.bzl", "pkg_tar")

package(
    default_visibility = ["//visibility:public"],
)

filegroup(
    name = "git-head-file",
    srcs = [
        ".git/HEAD",
    ],
)

filegroup(
    name = "dot-git-dir",
    srcs = [
        ".git",
    ],
)

pkg_tar(
    # for removing an installation, could probably go into prerm
    name = "runtime-util",
    srcs = [
        "scripts/nuke.sh",
        "scripts/runtime_util.sh",
    ],
    package_dir = "/opt/radix",
)

genrule(
    name = "ocean-omnidriver",
    srcs = ["@ocean-omnidriver-linux//file"],
    outs = [
        "HighResTiming.jar",
        "libcommon.so",
        "libNatHRTiming.so",
        "libNatUSB.so",
        "libOmniDriver.so",
        "OmniDriver.jar",
        "OOIUtils.jar",
        "SPAM.jar",
        "UniRS232.jar",
        "UniUSB.jar",
        "xpp3_min-1.1.3.4.M.jar",
        "xstream-1.1.2.jar",
    ],
    cmd = """
	mkdir out &&
	pwd &&
	$(location @tclkit//file) $(location @bitrock-unpacker//file) $(location @ocean-omnidriver-linux//file) out &&
	patchelf --set-rpath '$$ORIGIN' out/default/programfiles/OOI_HOME/libNatUSB.so &&
	echo "$(OUTS)" | tr \" \" \"\\n\" | xargs -n 1 -I {} sh -c "echo \\"{}\\" | egrep -o \\"([^\/]+\$$)\\" | xargs echo | xargs -I {} -n 1 cp out/default/programfiles/OOI_HOME/{} bazel-out/k8-fastbuild/bin/"
    """,
    local = True,
    tools = [
        "@bitrock-unpacker//file",
        "@tclkit//file",
    ],
)
