load("@rules_pkg//:pkg.bzl", "pkg_deb", "pkg_tar")
load("@io_bazel_rules_scala//scala:scala.bzl", "scala_binary", "scala_library")
load("//tools:workspace_status.bzl", "workspace_status")
load("//tools:windows.bzl", "pkg_msi")
load("//tools:timberland.bzl", "timberland_module")

#load("@bazel_skylib//rules:common_settings.bzl", "string_flag")
load("//tools:rpm.bzl", "rpm_package")
load("//tools:0install.bzl", "zeroinstall_package")
load("//tools:proguard.bzl", "services_pkg_tar")

package(
    default_visibility = ["//visibility:public"],
)

#string_flag(
#    name = "runtime_target",
#    build_setting_default = "amd64",  # Currently works via default in all select()s
#)
#
#config_setting(
#    name = "aarch64",
#    flag_values = {":runtime_target": "aarch64"},
#)
#
#config_setting(
#    name = "windows",
#    flag_values = {":runtime_target": "windows"},
#)
#
#config_setting(
#    name = "amd64",
#    flag_values = {":runtime_target": "amd64"},
#)

# timberland sources itself
TIMBERLAND_DEPENDENCIES = [
    "//utils/helm:helm-jvm",
    "@third_party//3rdparty/jvm/com/bertramlabs/plugins:hcl4j",
    "@third_party//3rdparty/jvm/com/github/xuwei_k:optparse_applicative",
    "@third_party//3rdparty/jvm/com/lihaoyi:ammonite_ops",
    "@third_party//3rdparty/jvm/com/outr:scribe",
    "@third_party//3rdparty/jvm/dnsjava",
    "@third_party//3rdparty/jvm/io/circe:circe_core",
    "@third_party//3rdparty/jvm/io/circe:circe_derivation",
    "@third_party//3rdparty/jvm/io/circe:circe_generic_extras",
    "@third_party//3rdparty/jvm/io/circe:circe_literal",
    "@third_party//3rdparty/jvm/io/circe:circe_optics",
    "@third_party//3rdparty/jvm/org/fusesource/jansi",
    "@third_party//3rdparty/jvm/org/scala_graph:graph_core",
    "@third_party//3rdparty/jvm/org/slf4j:slf4j_nop",
    "@third_party//3rdparty/jvm/com/github/tototoshi:scala_csv",
    "@third_party//3rdparty/jvm/com/profesorfalken:jPowerShell",
]

#TODO collapse the namespace, so "core_srcs" aren't from launch/, runtime/, util/...
#it's shared dependencies that timberland the commandline tool
#has with the in-container process
TIMBERLAND_SRCS = [
    "src/main/scala/defns.scala",
    "src/main/scala/cli.scala",
    "src/main/scala/launch/daemonutil.scala",
    "src/main/scala/launch/dns.scala",
    "src/main/scala/runtime/auth.scala",
    "src/main/scala/runtime/wan.scala",
    "src/main/scala/flags/featureFlags.scala",
    "src/main/scala/flags/configGen.scala",
    "src/main/scala/flags/depGraph.scala",
    "src/main/scala/flags/tfGen.scala",
    "src/main/scala/flags/tfParser.scala",
    "src/main/scala/flags/hooks/oktaAuthConfig.scala",
    "src/main/scala/flags/hooks/ensureSupported.scala",
    "src/main/scala/flags/hooks/FlagHook.scala",
    "src/main/scala/flags/hooks/oauthConfig.scala",
    "src/main/scala/flags/hooks/messagingConfig.scala",
    "src/main/scala/runtime/services.scala",  # TODO split this target up since Disco depends on this
    "src/main/scala/util/VaultStarter.scala",
    "src/main/scala/util/util.scala",
    "src/main/scala/EnvironmentVariables.scala",
]

scala_library(
    name = "timberland-lib",
    srcs = TIMBERLAND_SRCS,
    resources = glob([
        "src/main/resources/reference.conf",
    ]),
    visibility = ["//visibility:public"],
    exports = TIMBERLAND_DEPENDENCIES,
    deps = TIMBERLAND_DEPENDENCIES,
)

scala_binary(
    name = "timberland-bin",
    srcs = ["src/main/scala/runner.scala"],
    main_class = "com.radix.timberland.runner",
    deps = [":timberland-lib"],
)

# timberland runtime binary dependencies
# timberland runtime configuration files for binary dependencies
# drivers, and their bundling
# timberland platform-specific installers
# misc.

scala_binary(
    name = "timberland-test",
    srcs = ["src/main/scala/runner.scala"],
    main_class = "com.radix.timberland.TestApp",
    deps = [":timberland-lib"],
)

filegroup(
    name = "timberland-filegroup",
    srcs = [":timberland-bin_deploy.jar"],
)

pkg_tar(
    name = "timberland-executables",
    srcs = [
        ":timberland-bin",
        ":timberland-bin_deploy.jar",
    ],
    package_dir = "/opt/radix",
    strip_prefix = "/",
)

pkg_tar(
    name = "timberland-vault",
    srcs = [
        "assets/systemd/vault.env.conf",
        "assets/vault/actor-acl-token-policy.hcl",
        "assets/vault/aws-cred-role.json",
        "assets/vault/messaging-policy.hcl",
        "assets/vault/read-flag-config-policy.hcl",
        "assets/vault/read-rusers-policy.hcl",
        "assets/vault/remote-access-policy.hcl",
        "assets/vault/tls-cert-policy.hcl",
        "assets/vault/tls-cert-role.json",
        "assets/vault/vault_config.conf",
    ],
    mode = "0755",
    package_dir = "/opt/radix/timberland/vault",
    deps = [
        "//:vault",
        "//:vault-plugin-secrets-oauthapp",
    ],
)

pkg_tar(
    name = "timberland-exec",
    srcs = [
        "wrappers/timberland",
        "//timberland:timberland-bin_deploy.jar",
        "//timberland_svc:timberland-svc-bin_deploy.jar",
    ],
    mode = "0755",
    package_dir = "/opt/radix/timberland/exec",
    #    package_dir = "/usr/bin",
)

pkg_tar(
    name = "timberland-consul",
    srcs = [
        "assets/consul/actor-policy.hcl",
        "assets/consul/default-policy.hcl",
        "assets/systemd/consul.env.conf",
    ],
    package_dir = "/opt/radix/timberland/consul",
    deps = ["//:consul"],
)

pkg_tar(
    name = "timberland-containernetworking-cni-plugin",
    package_dir = "/opt/cni/bin",
    deps = ["//:containernetworking-cni-plugin"],
)

pkg_tar(
    name = "timberland-consul-config",
    srcs = [
        "assets/consul/consul-client.json",
        "assets/consul/consul-server.json",
    ],
    mode = "0755",
    package_dir = "/opt/radix/timberland/consul",
)

pkg_tar(
    name = "timberland-consul-template",
    srcs = [
        "assets/consul-template/config.hcl",
        "assets/consul-template/config-windows.hcl",
        "assets/consul-template/refresh-all.bat",
        "assets/consul-template/refresh-consul.bat",
        "assets/consul-template/refresh-nomad.bat",
        "@windows-kill//:windows-kill_x64_1.1.4_lib_release/windows-kill.exe",
    ],
    package_dir = "/opt/radix/timberland/consul-template",
    deps = ["//:consul-template"],
)

pkg_tar(
    name = "timberland-consul-template-ca",
    srcs = [
        "assets/consul-template/ca/cert.pem.tpl",
        "assets/consul-template/ca/combined.pem.tpl",
    ],
    package_dir = "/opt/radix/timberland/consul-template/ca",
)

pkg_tar(
    name = "timberland-consul-template-cli",
    srcs = [
        "assets/consul-template/cli/cert.pem.tpl",
        "assets/consul-template/cli/key.pem.tpl",
    ],
    package_dir = "/opt/radix/timberland/consul-template/cli",
)

pkg_tar(
    name = "timberland-consul-template-consul",
    srcs = [
        "assets/consul-template/consul/cert.pem.tpl",
        "assets/consul-template/consul/key.pem.tpl",
    ],
    package_dir = "/opt/radix/timberland/consul-template/consul",
)

pkg_tar(
    name = "timberland-consul-template-dns",
    srcs = [
        "assets/consul-template/resolv.conf.tpl",
    ],
    package_dir = "/opt/radix/timberland/consul-template",
)

pkg_tar(
    name = "timberland-consul-template-nomad",
    srcs = [
        "assets/consul-template/nomad/cert.pem.tpl",
        "assets/consul-template/nomad/cli-cert.pem.tpl",
        "assets/consul-template/nomad/cli-key.pem.tpl",
        "assets/consul-template/nomad/key.pem.tpl",
    ],
    package_dir = "/opt/radix/timberland/consul-template/nomad",
)

pkg_tar(
    name = "timberland-consul-template-vault",
    srcs = [
        "assets/consul-template/vault/cert.pem.tpl",
        "assets/consul-template/vault/key.pem.tpl",
    ],
    package_dir = "/opt/radix/timberland/consul-template/vault",
)

pkg_tar(
    name = "timberland-nomad",
    srcs = [
        "assets/nomad/nomad-windows.hcl",
        "assets/systemd/nomad.env.conf",
    ],
    package_dir = "/opt/radix/timberland/nomad",
    deps = ["//:nomad"],
)

pkg_tar(
    name = "timberland-nomad-config",
    srcs = [
        "assets/nomad/config/nomad.hcl",
    ],
    mode = "0755",
    package_dir = "/opt/radix/timberland/nomad/config",
)

pkg_tar(
    name = "timberland-systemd-services",
    srcs = [
        "assets/systemd/consul.service",
        "assets/systemd/nomad.service",
        "assets/systemd/timberland-after-startup.service",
        "assets/systemd/timberland-svc.service",
        "assets/systemd/vault.service",
    ],
    package_dir = "/etc/systemd/system",
)

pkg_tar(
    name = "timberland-networkd",
    srcs = [
        "assets/networkd/10-radix-consul",
        "assets/networkd/10-radix-nomad",
    ],
    package_dir = "/etc/networkd-dispatcher/routable.d",
)

timberland_services = []

services_pkg_tar(
    name = "timberland-services-proguard",
    srcs = timberland_services,
    package_dir = "/opt/radix/services",
    target_compatible_with = [
        "//tools:packaging",
        "//tools:build_for_windows",
    ],
    use_proguard = True,
)

services_pkg_tar(
    name = "timberland-services",
    srcs = timberland_services,
    package_dir = "/opt/radix/services",
    use_proguard = False,
)

pkg_tar(
    name = "timberland-cni-config",
    srcs = [
        "assets/container_networking/cni.conf",
    ],
    package_dir = "/etc/sysctl.d/",
)

pkg_tar(
    name = "timberland-windows-startup-script",
    srcs = [
        "assets/windows/afterstartup.bat",
    ],
    package_dir = "/opt/radix/timberland/",
)

workspace_status(
    name = "commit-message",
    variable = "STABLE_COMMIT_MSG",
)

workspace_status(
    name = "deb-description",
    variable = "STABLE_DEB_DESCRIPTION",
)

workspace_status(
    name = "release-name",
    variable = "STABLE_RELEASE_NAME",
)

workspace_status(
    name = "commit-hash",
    variable = "COMMIT_HASH",
    visibility = ["//visibility:public"],
)

pkg_tar(
    name = "nssm-tar",
    srcs = ["@nssm//:nssm-2.24/win32/nssm.exe"],
    package_dir = "/opt/radix/timberland",
)

pkg_tar(
    name = "release-name-tar",
    srcs = [
        "commit-message",
        "release-name",
    ],
    package_dir = "/opt/radix/timberland",
)

pkg_tar(
    name = "commit-hash-tar",
    srcs = [
        "commit-hash",
    ],
    package_dir = "/opt/radix/services/",
)

pkg_tar(
    name = "timberland-avahi",
    srcs = [
        "assets/avahi/avahi-autoipd.action",
    ],
    package_dir = "/opt/radix/timberland/avahi",
)

pkg_tar(
    name = "timberland-full-tar-proguard",
    target_compatible_with = ["//tools:packaging"],
    deps = [
        ":timberland-services-proguard",
        ":timberland-slim-tar",
    ],
)

pkg_tar(
    name = "timberland-full-tar",
    deps = [
        ":timberland-services",
        ":timberland-slim-tar",
        # "//interface:frontend-vite",
    ],
)

pkg_tar(
    name = "timberland-slim-tar",
    deps = [
        ":commit-hash-tar",
        ":nssm-tar",
        ":release-name-tar",
        ":terraform-deps",
        ":terraform-main",
        ":terraform-modules",
        ":timberland-avahi",
        ":timberland-cni-config",
        ":timberland-consul",
        ":timberland-consul-config",
        ":timberland-consul-template",
        ":timberland-consul-template-ca",
        ":timberland-consul-template-cli",
        ":timberland-consul-template-consul",
        ":timberland-consul-template-dns",
        ":timberland-consul-template-nomad",
        ":timberland-consul-template-vault",
        ":timberland-containernetworking-cni-plugin",
        ":timberland-exec",
        ":timberland-networkd",
        ":timberland-nomad",
        ":timberland-nomad-config",
        ":timberland-systemd-services",
        ":timberland-vault",
        ":timberland-windows-startup-script",
    ],
)

pkg_msi(
    name = "timberland-windows",
    data = ":timberland-full-tar-proguard",
    data_base_dir = "./opt/radix",
    description = "Radix_Timberland_deployment",
    manufacturer = "Radix_Labs",
    package = "radix-timberland",
    package_guid = "{eabaa8a7-e277-46ed-8602-4e4212afa5ec}",
    post_install_script = "assets/windows/postinst.bat",
    target_compatible_with = [
        "//tools:packaging",
        "//tools:build_for_windows",
    ],
    version = "0.1",
)

pkg_msi(
    name = "timberland-windows-slim",
    data = ":timberland-slim-tar",
    data_base_dir = "./opt/radix",
    description = "Radix_Timberland_deployment",
    manufacturer = "Radix_Labs",
    package = "radix-timberland-slim",
    package_guid = "{eabaa8a7-e277-46ed-8602-4e4212afa5ec}",
    post_install_script = "assets/windows/postinst.bat",
    target_compatible_with = [
        "//tools:packaging",
        "//tools:build_for_windows",
    ],
    version = "0.1",
)

pkg_deb(
    name = "timberland-deb",
    architecture = "all",
    data = ":timberland-full-tar",
    depends = [
        "docker.io | docker-ce",
        "openjdk-11-jre-headless",
        "net-tools",
        "avahi-utils",
    ],
    description_file = ":deb-description",
    maintainer = "Alex Hulbert",
    package = "radix-timberland",
    postinst = "assets/debian/postinst.sh",
    prerm = "assets/debian/prerm.sh",
    version = "0.1",  # filename will have a blank version bc file can't be read at analysis time
)

zeroinstall_package(
    name = "timberland-0install",
    output = "timberland-0install.tar",
    data = ":timberland-full-tar",
    assets =
    glob(
        ["assets/zeroinstall/*.xml"]
    ) + [
        "assets/zeroinstall/install",
        "assets/zeroinstall/uninstall",
        "assets/zeroinstall/postinst",
    ]
)

zeroinstall_package(
    name = "timberland-slim-0install",
    output = "timberland-slim-0install.tar",
    data = ":timberland-slim-tar",
    assets =
    glob(
        ["assets/zeroinstall/*.xml"],
    ) + [
        "assets/zeroinstall/install",
        "assets/zeroinstall/uninstall",
        "assets/zeroinstall/postinst",
    ]
)

pkg_deb(
    name = "timberland-slim-deb",
    architecture = "all",
    data = ":timberland-slim-tar",
    depends = [
        "docker.io | docker-ce",
        "openjdk-11-jre-headless",
        "net-tools",
        "avahi-utils",
    ],
    description_file = ":deb-description",
    maintainer = "Alex Hulbert",
    package = "radix-timberland-slim",
    postinst = "assets/debian/postinst.sh",
    prerm = "assets/debian/prerm.sh",
    target_compatible_with = [
        "//tools:packaging",
        "//tools:build_for_debian",
        "//tools:build_for_linux",
    ],
    version = "0.1",  # filename will have a blank version bc file can't be read at analysis time
)

rpm_package(
    name = "timberland-rpm",
    data = ":timberland-full-tar",
    rpm_file = "timberland-rpm-all.rpm",
    spec_template = "assets/redhat/rpm.spec",
    target_compatible_with = [
        "//tools:packaging",
        "//tools:build_for_centos",
        "//tools:build_for_linux",
    ],
)

rpm_package(
    name = "timberland-slim-rpm",
    data = ":timberland-slim-tar",
    rpm_file = "timberland-rpm-slim.rpm",
    spec_template = "assets/redhat/rpm.spec",
    target_compatible_with = [
        "//tools:packaging",
        "//tools:build_for_centos",
        "//tools:build_for_linux",
    ],
)

sh_binary(
    name = "timberland-ec2-dev",
    srcs = [
        "src/test/ec2_integration_runner.sh",
    ],
    args = [
        "persist",
    ],
    data = [
        "src/test/install-deb.sh",
        "src/test/install-rpm.sh",
        "src/test/service_test.py",
        ":radix-timberland_0.1_all.deb",
        ":timberland-rpm-all.rpm",
    ],
    #tags = ["no-sandbox"],
    target_compatible_with = [
        "//tools:packaging",
        "//tools:build_for_centos",
        "//tools:build_for_debian",
        "//tools:build_for_linux",
    ],
)

sh_test(
    name = "timberland-ci",
    size = "large",
    timeout = "eternal",
    srcs = [
        "src/test/ec2_integration_runner.sh",
    ],
    data = [
        "src/test/gather-results.sh",
        "src/test/install-deb.sh",
        "src/test/install-rpm.sh",
        "src/test/service_test.py",
        "src/test/shutdown-after-uptime.sh",
        ":radix-timberland_0.1_all.deb",
        ":timberland-rpm-all.rpm",
    ],
    tags = ["no-sandbox"],
    target_compatible_with = [
        "//tools:packaging",
        "//tools:build_for_centos",
        "//tools:build_for_debian",
        "//tools:build_for_linux",
    ],
)

timberland_module(
    name = "elasticsearch",
    terraform_srcs = [
        "assets/terraform/elasticsearch/elasticsearch.tmpl",
        "assets/terraform/elasticsearch/kibana.tmpl",
        "assets/terraform/elasticsearch/main.tf",
        "assets/terraform/elasticsearch/outputs.tf",
        "assets/terraform/elasticsearch/variables.tf",
    ],
)

timberland_module(
    name = "nginx",
    terraform_srcs = [
        "assets/terraform/nginx/main.tf",
        "assets/terraform/nginx/nginx.tmpl",
        "assets/terraform/nginx/outputs.tf",
        "assets/terraform/nginx/variables.tf",
    ],
)

timberland_module(
    name = "kafka",
    terraform_srcs = [
        "assets/terraform/kafka/kafka.tmpl",
        "assets/terraform/kafka/main.tf",
        "assets/terraform/kafka/outputs.tf",
        "assets/terraform/kafka/variables.tf",
    ],
)

timberland_module(
    name = "kafka_companions",
    terraform_srcs = [
        "assets/terraform/kafka_companions/kafka_companions.tmpl",
        "assets/terraform/kafka_companions/main.tf",
        "assets/terraform/kafka_companions/outputs.tf",
        "assets/terraform/kafka_companions/variables.tf",
    ],
)

timberland_module(
    name = "yugabyte",
    terraform_srcs = [
        "assets/terraform/yugabyte/main.tf",
        "assets/terraform/yugabyte/outputs.tf",
        "assets/terraform/yugabyte/variables.tf",
        "assets/terraform/yugabyte/yb_masters.tmpl",
        "assets/terraform/yugabyte/yb_tservers.tmpl",
    ],
)

timberland_module(
    name = "zookeeper",
    terraform_srcs = [
        "assets/terraform/zookeeper/main.tf",
        "assets/terraform/zookeeper/outputs.tf",
        "assets/terraform/zookeeper/variables.tf",
        "assets/terraform/zookeeper/zookeeper.tmpl",
    ],
)

pkg_tar(
    name = "terraform-modules",
    deps = [
        ":elasticsearch-terraform",
        ":kafka-terraform",
        ":kafka_companions-terraform",
        ":nginx-terraform",
        ":yugabyte-terraform",
        ":zookeeper-terraform",
    ],
)

pkg_tar(
    name = "terraform-deps",
    package_dir = "/opt/radix/timberland/terraform",
    deps = [
        ":terraform-plugins",
        "//:terraform",
    ],
)

pkg_tar(
    name = "terraform-plugins",
    package_dir = "plugins",
    deps = [
        "//:terraform-provider-consul",
        "//:terraform-provider-nomad",
        "//:terraform-provider-vault",
    ],
)

pkg_tar(
    name = "terraform-main",
    srcs = [
        "assets/terraform/main/backend.tf",
        "assets/terraform/main/outputs.tf",
        "assets/terraform/main/variables.tf",
    ],
    package_dir = "/opt/radix/timberland/terraform/modules",
)

scala_binary(
    name = "debug",
    srcs = [],
    main_class = "ammonite.Main",
    deps = [
        ":timberland-lib",
        "@third_party//3rdparty/jvm/com/lihaoyi:ammonite_2_13_8",
    ],
)

