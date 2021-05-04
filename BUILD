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
