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
echo "$(OUTS)" | tr " " "\\n" | xargs -n 1 -I {} sh -c "echo \\"{}\\" | egrep -o \\"([^\\/]+\\$$)\\" | xargs echo | xargs -I {} -n 1 cp out/default/programfiles/OOI_HOME/{} bazel-out/k8-fastbuild/bin/"
    """,
    local = True,
    tags = ["no-cache"],
    tools = [
        "@bitrock-unpacker//file",
        "@tclkit//file",
    ],
    target_compatible_with = [
        "@platforms//cpu:x86_64",
        "@platforms//os:linux",
    ]
)

# consul
pkg_tar(
    name = "consul_macos",
    srcs = select({
        "//tools:build_for_aarch64": ["@consul_macos_aarch64//:consul"],
        "//tools:build_for_x64": ["@consul_macos_x64//:consul"],
    }),
    target_compatible_with = ["//tools:build_for_macos"]
)

pkg_tar(
    name = "consul_linux",
    srcs = select({
        "//tools:build_for_aarch64": ["@consul_linux_aarch64//:consul"],
        "//tools:build_for_x64": ["@consul_linux_x64//:consul"],
    }),
    target_compatible_with = ["//tools:build_for_linux"]
)

pkg_tar(
    name = "consul",
    srcs = select({
        "//tools:build_for_macos": [],
        "//tools:build_for_linux": [],
        "//tools:build_for_windows": ["@consul_windows_x64//:consul.exe"],
    }),
    deps = select({
        "//tools:build_for_macos": [":consul_macos"],
        "//tools:build_for_linux": [":consul_linux"],
        "//tools:build_for_windows": [],
    })
)

# consul-template
# does not have a macos_aarch64 build

pkg_tar(
    name = "consul-template_linux",
    srcs = select({
        "//tools:build_for_aarch64": ["@consul-template_linux_aarch64//:consul-template"],
        "//tools:build_for_x64": ["@consul-template_linux_x64//:consul-template"],
    }),
    target_compatible_with = ["//tools:build_for_linux"]
)

pkg_tar(
    name = "consul-template",
    srcs = select({
        "//tools:build_for_macos": ["@consul-template_macos_x64//:consul-template"],
        "//tools:build_for_linux": [],
        "//tools:build_for_windows": ["@consul-template_windows_x64//:consul-template.exe"],
    }),
    deps = select({
        "//tools:build_for_macos": [],
        "//tools:build_for_linux": [":consul-template_linux"],
        "//tools:build_for_windows": [],
    }),
)

# vault
pkg_tar(
    name = "vault_macos",
    srcs = select({
        "//tools:build_for_aarch64": ["@vault_macos_aarch64//:vault"],
        "//tools:build_for_x64": ["@vault_macos_x64//:vault"],
    }),
    target_compatible_with = ["//tools:build_for_macos"]
)

pkg_tar(
    name = "vault_linux",
    srcs = select({
        "//tools:build_for_aarch64": ["@vault_linux_aarch64//:vault"],
        "//tools:build_for_x64": ["@vault_linux_x64//:vault"],
    }),
    target_compatible_with = ["//tools:build_for_linux"]
)

pkg_tar(
    name = "vault",
    srcs = select({
        "//tools:build_for_macos": [],
        "//tools:build_for_linux": [],
        "//tools:build_for_windows": ["@vault_windows_x64//:vault.exe"],
    }),
    deps = select({
        "//tools:build_for_macos": [":vault_macos"],
        "//tools:build_for_linux": [":vault_linux"],
        "//tools:build_for_windows": [],
    }),
)

# vault-plugin-secrets-oauthapp
# does not have a macos_aarch64 build
pkg_tar(
    name = "vault-plugin-secrets-oauthapp_linux",
    srcs = select({
        "//tools:build_for_aarch64": ["@vault-plugin-secrets-oauthapp_linux_aarch64//:vault-plugin-secrets-oauthapp"],
        "//tools:build_for_x64": ["@vault-plugin-secrets-oauthapp_linux_x64//:vault-plugin-secrets-oauthapp"],
    }),
    target_compatible_with = ["//tools:build_for_linux"]
)

pkg_tar(
    name = "vault-plugin-secrets-oauthapp",
    srcs = select({
        "//tools:build_for_macos": ["@vault-plugin-secrets-oauthapp_macos_x64//:vault-plugin-secrets-oauthapp"],
        "//tools:build_for_linux": [],
        "//tools:build_for_windows": ["@vault-plugin-secrets-oauthapp_windows_x64//:vault-plugin-secrets-oauthapp.exe"],
    }),
    deps = select({
        "//tools:build_for_macos": [],
        "//tools:build_for_linux": [":vault-plugin-secrets-oauthapp_linux"],
        "//tools:build_for_windows": [],
    }),
)


# nomad
pkg_tar(
    name = "nomad_linux",
    srcs = select({
        "//tools:build_for_aarch64": ["@nomad_linux_aarch64//:nomad"],
        "//tools:build_for_x64": ["@nomad_linux_x64//:nomad"],
    }),
    target_compatible_with = ["//tools:build_for_linux"]
)

pkg_tar(
    name = "nomad",
    srcs = select({
        "//tools:build_for_macos": ["@nomad_macos_x64//:nomad"],
        "//tools:build_for_linux": [],
        "//tools:build_for_windows": ["@nomad_windows_x64//:nomad.exe"],
    }),
    deps = select({
        "//tools:build_for_macos": [],
        "//tools:build_for_linux": [":nomad_linux"],
        "//tools:build_for_windows": [],
    }),
)



# terraform
pkg_tar(
    name = "terraform_macos",
    srcs = select({
        "//tools:build_for_aarch64": ["@terraform_macos_aarch64//:terraform"],
        "//tools:build_for_x64": ["@terraform_macos_x64//:terraform"],
    }),
    target_compatible_with = ["//tools:build_for_macos"]
)

pkg_tar(
    name = "terraform_linux",
    srcs = select({
        "//tools:build_for_aarch64": ["@terraform_linux_aarch64//:terraform"],
        "//tools:build_for_x64": ["@terraform_linux_x64//:terraform"],
    }),
    target_compatible_with = ["//tools:build_for_linux"]
)

pkg_tar(
    name = "terraform",
    srcs = select({
        "//tools:build_for_macos": [],
        "//tools:build_for_linux": [],
        "//tools:build_for_windows": ["@terraform_windows_x64//:terraform.exe"],
    }),
    deps = select({
        "//tools:build_for_macos": [":terraform_macos"],
        "//tools:build_for_linux": [":terraform_linux"],
        "//tools:build_for_windows": [],
    }),
)


# terraform-provider-nomad
pkg_tar(
    name = "terraform-provider-nomad_macos",
    srcs = select({
        "//tools:build_for_aarch64": ["@terraform-provider-nomad_macos_aarch64//file"],
        "//tools:build_for_x64": ["@terraform-provider-nomad_macos_x64//file"],
    }),
    target_compatible_with = ["//tools:build_for_macos"]
)

pkg_tar(
    name = "terraform-provider-nomad_linux",
    srcs = select({
        "//tools:build_for_aarch64": ["@terraform-provider-nomad_linux_aarch64//file"],
        "//tools:build_for_x64": ["@terraform-provider-nomad_linux_x64//file"],
    }),
    target_compatible_with = ["//tools:build_for_linux"]
)

pkg_tar(
    name = "terraform-provider-nomad",
    srcs = select({
        "//tools:build_for_macos": [],
        "//tools:build_for_linux": [],
        "//tools:build_for_windows": ["@terraform-provider-nomad_windows_x64//file"],
    }),
    deps = select({
        "//tools:build_for_macos": [":terraform-provider-nomad_macos"],
        "//tools:build_for_linux": [":terraform-provider-nomad_linux"],
        "//tools:build_for_windows": [],
    }),
    package_dir = "registry.terraform.io/hashicorp/nomad",
)


# terraform-provider-consul
pkg_tar(
    name = "terraform-provider-consul_macos",
    srcs = select({
        "//tools:build_for_aarch64": ["@terraform-provider-consul_macos_aarch64//file"],
        "//tools:build_for_x64": ["@terraform-provider-consul_macos_x64//file"],
    }),
    target_compatible_with = ["//tools:build_for_macos"]
)

pkg_tar(
    name = "terraform-provider-consul_linux",
    srcs = select({
        "//tools:build_for_aarch64": ["@terraform-provider-consul_linux_aarch64//file"],
        "//tools:build_for_x64": ["@terraform-provider-consul_linux_x64//file"],
    }),
    target_compatible_with = ["//tools:build_for_linux"]
)

pkg_tar(
    name = "terraform-provider-consul",
    srcs = select({
        "//tools:build_for_macos": [],
        "//tools:build_for_linux": [],
        "//tools:build_for_windows": ["@terraform-provider-consul_windows_x64//file"],
    }),
    deps = select({
        "//tools:build_for_macos": [":terraform-provider-consul_macos"],
        "//tools:build_for_linux": [":terraform-provider-consul_linux"],
        "//tools:build_for_windows": [],
    }),
    package_dir = "registry.terraform.io/hashicorp/consul",
)

# terraform-provider-vault
pkg_tar(
    name = "terraform-provider-vault_macos",
    srcs = select({
        "//tools:build_for_aarch64": ["@terraform-provider-vault_macos_aarch64//file"],
        "//tools:build_for_x64": ["@terraform-provider-vault_macos_x64//file"],
    }),
    target_compatible_with = ["//tools:build_for_macos"]
)

pkg_tar(
    name = "terraform-provider-vault_linux",
    srcs = select({
        "//tools:build_for_aarch64": ["@terraform-provider-vault_linux_aarch64//file"],
        "//tools:build_for_x64": ["@terraform-provider-vault_linux_x64//file"],
    }),
    target_compatible_with = ["//tools:build_for_linux"]
)

pkg_tar(
    name = "terraform-provider-vault",
    srcs = select({
        "//tools:build_for_macos": [],
        "//tools:build_for_linux": [],
        "//tools:build_for_windows": ["@terraform-provider-vault_windows_x64//file"],
    }),
    deps = select({
        "//tools:build_for_macos": [":terraform-provider-vault_macos"],
        "//tools:build_for_linux": [":terraform-provider-vault_linux"],
        "//tools:build_for_windows": [],
    }),
    package_dir = "registry.terraform.io/hashicorp/vault",
)
# containernetworking-cni-plugin
pkg_tar(
    name = "containernetworking-cni-plugin_linux",
    srcs = select({
        "//tools:build_for_aarch64": [
            "@containernetworking-cni-plugin_linux_aarch64//:bandwidth",
            "@containernetworking-cni-plugin_linux_aarch64//:bridge",
            "@containernetworking-cni-plugin_linux_aarch64//:dhcp",
            "@containernetworking-cni-plugin_linux_aarch64//:firewall",
            "@containernetworking-cni-plugin_linux_aarch64//:flannel",
            "@containernetworking-cni-plugin_linux_aarch64//:host-device",
            "@containernetworking-cni-plugin_linux_aarch64//:host-local",
            "@containernetworking-cni-plugin_linux_aarch64//:ipvlan",
            "@containernetworking-cni-plugin_linux_aarch64//:loopback",
            "@containernetworking-cni-plugin_linux_aarch64//:macvlan",
            "@containernetworking-cni-plugin_linux_aarch64//:portmap",
            "@containernetworking-cni-plugin_linux_aarch64//:ptp",
            "@containernetworking-cni-plugin_linux_aarch64//:sbr",
            "@containernetworking-cni-plugin_linux_aarch64//:static",
            "@containernetworking-cni-plugin_linux_aarch64//:tuning",
            "@containernetworking-cni-plugin_linux_aarch64//:vlan",
        ],
        "//tools:build_for_x64":[
            "@containernetworking-cni-plugin_linux_x64//:bandwidth",
            "@containernetworking-cni-plugin_linux_x64//:bridge",
            "@containernetworking-cni-plugin_linux_x64//:dhcp",
            "@containernetworking-cni-plugin_linux_x64//:firewall",
            "@containernetworking-cni-plugin_linux_x64//:flannel",
            "@containernetworking-cni-plugin_linux_x64//:host-device",
            "@containernetworking-cni-plugin_linux_x64//:host-local",
            "@containernetworking-cni-plugin_linux_x64//:ipvlan",
            "@containernetworking-cni-plugin_linux_x64//:loopback",
            "@containernetworking-cni-plugin_linux_x64//:macvlan",
            "@containernetworking-cni-plugin_linux_x64//:portmap",
            "@containernetworking-cni-plugin_linux_x64//:ptp",
            "@containernetworking-cni-plugin_linux_x64//:sbr",
            "@containernetworking-cni-plugin_linux_x64//:static",
            "@containernetworking-cni-plugin_linux_x64//:tuning",
            "@containernetworking-cni-plugin_linux_x64//:vlan",
        ],
    }),
    target_compatible_with = ["//tools:build_for_linux"]
)

pkg_tar(
    name = "containernetworking-cni-plugin",
    srcs = select({
        "//tools:build_for_linux": [],
        "//tools:build_for_windows": [
            "@containernetworking-cni-plugin_windows_x64//:flannel.exe",
            "@containernetworking-cni-plugin_windows_x64//:host-local.exe",
            "@containernetworking-cni-plugin_windows_x64//:win-bridge.exe",
            "@containernetworking-cni-plugin_windows_x64//:win-overlay.exe",
        ],
       "//tools:build_for_macos": [] # macos does not support cni
    }),
    deps = select({
        "//tools:build_for_linux": [":containernetworking-cni-plugin_linux"],
        "//tools:build_for_windows": [],
        "//tools:build_for_macos": []
    })
)

# TODO arm
pkg_tar(
    name = "ipfs",
    srcs = select({
        "//tools:build_for_linux": ["@ipfs//:go-ipfs/ipfs"],
        "//tools:build_for_windows": ["@ipfs_win//:go-ipfs/ipfs.exe"],
        "//tools:build_for_macos": []
    })
)

foo = select({
        "//tools:build_for_linux": ["foo"],
        "//tools:build_for_windows": ["bar"],
        "//tools:build_for_macos": ["biz"]
})
