load("@rules_pkg//:pkg.bzl", "pkg_deb", "pkg_tar")
load("@io_bazel_rules_docker//docker/util:run.bzl", "container_run_and_commit", "container_run_and_extract")
load("@io_bazel_rules_docker//container:container.bzl", "container_image", "container_layer", "container_load", "container_push")
load("//tools:terraform.bzl", "dynamic_rpm_spec", "terraform_deployment", "terraform_module", "terraform_provider")
load("//tools:workspace_status.bzl", "workspace_status")
load("//tools:windows.bzl", "pkg_msi")
load("@bazel_skylib//rules:common_settings.bzl", "string_flag")
load("//tools:rpm.bzl", "rpm_package")
load("//tools:proguard.bzl", "services_pkg_tar")

package(
    default_visibility = ["//visibility:public"],
)

string_flag(
    name = "runtime_target",
    build_setting_default = "amd64",  # Currently works via default in all select()s
)

config_setting(
    name = "aarch64",
    flag_values = {":runtime_target": "aarch64"},
)

config_setting(
    name = "windows",
    flag_values = {":runtime_target": "windows"},
)

config_setting(
    name = "amd64",
    flag_values = {":runtime_target": "amd64"},
)

scala_library(
    name = "timberland-dependencies",
    exports = [
        "//shared/shared:shared-shared",
        "//utils/helm:helm-jvm",
        "//utils/sheets:sheets-interps",
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
        "@third_party//3rdparty/jvm/org/scala_lang/modules:scala_parser_combinators",
    ],
    deps = [
        "//shared/shared:shared-shared",
        "//utils/helm:helm-jvm",
        "//utils/sheets:sheets-interps",
        "@third_party//3rdparty/jvm/com/github/xuwei_k:optparse_applicative",
        "@third_party//3rdparty/jvm/com/lihaoyi:ammonite_ops",
        "@third_party//3rdparty/jvm/com/outr:scribe",
        "@third_party//3rdparty/jvm/io/circe:circe_core",
        "@third_party//3rdparty/jvm/io/circe:circe_derivation",
        "@third_party//3rdparty/jvm/io/circe:circe_generic_extras",
        "@third_party//3rdparty/jvm/io/circe:circe_optics",
        "@third_party//3rdparty/jvm/org/fusesource/jansi",
        "@third_party//3rdparty/jvm/org/scala_lang/modules:scala_parser_combinators",
        "@third_party//3rdparty/jvm/org/slf4j:slf4j_nop",
    ],
)

#TODO collapse the namespace, so "core_srcs" aren't from launch/, runtime/, util/...
#it's shared dependencies that timberland the commandline tool
#has with the in-container process
timberland_core_srcs = [
    "src/main/scala/defns.scala",
    "src/main/scala/launch/consulutil.scala",
    "src/main/scala/launch/daemonutil.scala",
    "src/main/scala/runtime/auth.scala",
    "src/main/scala/flags/config.scala",
    "src/main/scala/flags/featureFlags.scala",
    "src/main/scala/flags/flagConfiguration.scala",
    "src/main/scala/flags/hooks/awsAuthConfig.scala",
    "src/main/scala/flags/hooks/dockerAuthConfig.scala",
    "src/main/scala/flags/hooks/oktaAuthConfig.scala",
    "src/main/scala/flags/hooks/ensureSupported.scala",
    "src/main/scala/flags/hooks/FlagHook.scala",
    "src/main/scala/flags/hooks/oauthConfig.scala",
    "src/main/scala/runtime/install.scala",
    #"src/main/scala/runtime/services.scala",
    "src/main/scala/util/VaultStarter.scala",
    "src/main/scala/util/oauth.scala",
    "src/main/scala/util/LogTUI.scala",
    "src/main/scala/util/util.scala",
    "src/main/scala/util/UpdateJobSpecs.scala",
]

scala_library(
    name = "timberland-core-lib",
    srcs = timberland_core_srcs,
    resources = glob([
        "src/main/resources/reference.conf",
    ]),
    visibility = ["//visibility:public"],
    exports = [":timberland-dependencies"],
    deps = [
        ":timberland-dependencies",
    ],
)

timberland_service_control_srcs = [
    # why is this separate?
    "src/main/scala/runtime/services.scala",
]

scala_library(
    name = "timberland-service-control",
    srcs = timberland_service_control_srcs,
    visibility = [
        "//visibility:public",
    ],
    exports = [
        ":timberland-core-lib",
    ],
    deps = [
        ":timberland-core-lib",
    ],
)

scala_library(
    name = "timberland-lib",
    # Exclude the kafka and zookeeper sources
    srcs = glob(
        [
            "src/main/scala/**/*.scala",
        ],
        exclude = timberland_core_srcs + timberland_service_control_srcs,
    ),
    visibility = [
        "//timberland:__subpackages__",
    ],
    exports = [
        ":timberland-core-lib",
        ":timberland-service-control",
        "//shared/shared:shared-shared",
        "//utils/helm:helm-jvm",
        "//utils/sheets:sheets-interps",  #TODO remove this
        "@third_party//3rdparty/jvm/com/github/xuwei_k:optparse_applicative",
        "@third_party//3rdparty/jvm/com/lihaoyi:ammonite_ops",
        "@third_party//3rdparty/jvm/com/outr:scribe",
    ],
    deps = [
        ":timberland-core-lib",
        ":timberland-service-control",
        #      ":timberland-update-lib",
        "//utils/helm:helm-jvm",
        "//utils/sheets:sheets-interps",  #TODO remove this
        "@third_party//3rdparty/jvm/com/github/tototoshi:scala_csv",
        "@third_party//3rdparty/jvm/com/profesorfalken:jPowerShell",
    ],
)

scala_binary(
    name = "timberland-bin",
    srcs = ["src/main/scala/runner.scala"],
    main_class = "com.radix.timberland.runner",
    deps = [":timberland-lib"],
)

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
               "//timberland/assets:vault/read-message-targets-policy.hcl",
               "//timberland/assets:systemd/vault.env.conf",
               "//timberland/assets:vault/actor-acl-token-policy.hcl",
               "//timberland/assets:vault/read-flag-config-policy.hcl",
               "//timberland/assets:vault/remote-access-policy.hcl",
               "//timberland/assets:vault/aws-cred-role.json",
               "//timberland/assets:vault/tls-cert-policy.hcl",
               "//timberland/assets:vault/tls-cert-role.json",
               "//timberland/assets:vault/vault_config.conf",
           ] + select({
               "aarch64": ["@vault_arm//:vault"],
               "windows": ["@vault_win//:vault.exe"],
               "//conditions:default": ["@vault"],
           }) +
           select({
               "aarch64": ["@vault-plugin-secrets-oauthapp_arm//:vault-plugin-secrets-oauthapp"],
               "windows": ["@vault-plugin-secrets-oauthapp_win//:vault-plugin-secrets-oauthapp.exe"],
               "//conditions:default": ["@vault-plugin-secrets-oauthapp"],
           }),
    package_dir = "/opt/radix/timberland/vault",
)

pkg_tar(
    name = "timberland-exec",
    srcs = [
        "wrappers/timberland",
        "//timberland:timberland-bin_deploy.jar",
        "//timberland_svc:timberland-svc-bin_deploy.jar",
    ],
    package_dir = "/opt/radix/timberland/exec",
    #    package_dir = "/usr/bin",
)

pkg_tar(
    name = "timberland-consul",
    srcs = [
        "//timberland/assets:consul/actor-policy.hcl",
        "//timberland/assets:consul/default-policy.hcl",
        "//timberland/assets:systemd/consul.env.conf",
    ] + select({
        "aarch64": ["@consul_arm//:consul"],
        "windows": ["@consul_win//:consul.exe"],
        "//conditions:default": ["@consul"],
    }),
    package_dir = "/opt/radix/timberland/consul",
)

pkg_tar(
    name = "timberland-containernetworking-cni-plugin",
    srcs = select({
        "aarch64": [
            "@containernetworking-cni-plugin_arm//:bandwidth",
            "@containernetworking-cni-plugin_arm//:bridge",
            "@containernetworking-cni-plugin_arm//:dhcp",
            "@containernetworking-cni-plugin_arm//:firewall",
            "@containernetworking-cni-plugin_arm//:flannel",
            "@containernetworking-cni-plugin_arm//:host-device",
            "@containernetworking-cni-plugin_arm//:host-local",
            "@containernetworking-cni-plugin_arm//:ipvlan",
            "@containernetworking-cni-plugin_arm//:loopback",
            "@containernetworking-cni-plugin_arm//:macvlan",
            "@containernetworking-cni-plugin_arm//:portmap",
            "@containernetworking-cni-plugin_arm//:ptp",
            "@containernetworking-cni-plugin_arm//:sbr",
            "@containernetworking-cni-plugin_arm//:static",
            "@containernetworking-cni-plugin_arm//:tuning",
            "@containernetworking-cni-plugin_arm//:vlan",
        ],
        "windows": [
            "@containernetworking-cni-plugin_win//:flannel.exe",
            "@containernetworking-cni-plugin_win//:host-local.exe",
            "@containernetworking-cni-plugin_win//:win-bridge.exe",
            "@containernetworking-cni-plugin_win//:win-overlay.exe",
        ],
        "//conditions:default": [
            "@containernetworking-cni-plugin//:bandwidth",
            "@containernetworking-cni-plugin//:bridge",
            "@containernetworking-cni-plugin//:dhcp",
            "@containernetworking-cni-plugin//:firewall",
            "@containernetworking-cni-plugin//:flannel",
            "@containernetworking-cni-plugin//:host-device",
            "@containernetworking-cni-plugin//:host-local",
            "@containernetworking-cni-plugin//:ipvlan",
            "@containernetworking-cni-plugin//:loopback",
            "@containernetworking-cni-plugin//:macvlan",
            "@containernetworking-cni-plugin//:portmap",
            "@containernetworking-cni-plugin//:ptp",
            "@containernetworking-cni-plugin//:sbr",
            "@containernetworking-cni-plugin//:static",
            "@containernetworking-cni-plugin//:tuning",
            "@containernetworking-cni-plugin//:vlan",
        ],
    }),
    package_dir = "/opt/cni/bin",
)

pkg_tar(
    name = "timberland-consul-config",
    srcs = [
        "//timberland/assets:consul/consul-client.json",
        "//timberland/assets:consul/consul-server.json",
    ],
    package_dir = "/opt/radix/timberland/consul",
)

pkg_tar(
    name = "timberland-consul-template",
    srcs = [
        "//timberland/assets:consul-template/config.hcl",
        "//timberland/assets:consul-template/config-windows.hcl",
        "//timberland/assets:systemd/consul-template.env.conf",
    ] + select({
        "aarch64": ["@consul-template_arm//:consul-template"],
        "windows": ["@consul-template_win//:consul-template.exe"],
        "//conditions:default": ["@consul-template"],
    }),
    package_dir = "/opt/radix/timberland/consul-template",
)

pkg_tar(
    name = "timberland-consul-template-dns",
    srcs = [
        "//timberland/assets:consul-template/resolv.conf.tpl",
    ],
    package_dir = "/opt/radix/timberland/consul-template",
)

pkg_tar(
    name = "timberland-consul-template-ca",
    srcs = [
        "//timberland/assets:consul-template/ca/cert.pem.tpl",
        "//timberland/assets:consul-template/ca/combined.pem.tpl",
    ],
    package_dir = "/opt/radix/timberland/consul-template/ca",
)

pkg_tar(
    name = "timberland-consul-template-cli",
    srcs = [
        "//timberland/assets:consul-template/cli/cert.pem.tpl",
        "//timberland/assets:consul-template/cli/key.pem.tpl",
    ],
    package_dir = "/opt/radix/timberland/consul-template/cli",
)

pkg_tar(
    name = "timberland-consul-template-consul",
    srcs = [
        "//timberland/assets:consul-template/consul/cert.pem.tpl",
        "//timberland/assets:consul-template/consul/key.pem.tpl",
    ],
    package_dir = "/opt/radix/timberland/consul-template/consul",
)

pkg_tar(
    name = "timberland-consul-template-nomad",
    srcs = [
        "//timberland/assets:consul-template/nomad/cert.pem.tpl",
        "//timberland/assets:consul-template/nomad/cli-cert.pem.tpl",
        "//timberland/assets:consul-template/nomad/cli-key.pem.tpl",
        "//timberland/assets:consul-template/nomad/key.pem.tpl",
    ],
    package_dir = "/opt/radix/timberland/consul-template/nomad",
)

pkg_tar(
    name = "timberland-consul-template-vault",
    srcs = [
        "//timberland/assets:consul-template/vault/cert.pem.tpl",
        "//timberland/assets:consul-template/vault/key.pem.tpl",
    ],
    package_dir = "/opt/radix/timberland/consul-template/vault",
)

pkg_tar(
    name = "timberland-nomad",
    srcs = [
        "//timberland/assets:systemd/nomad.env.conf",
    ] + select({
        "aarch64": ["@nomad_arm//:nomad"],
        "windows": [
            "@nomad_win//:nomad.exe",
            "//timberland/assets:nomad/nomad-windows.hcl",
        ],
        "//conditions:default": ["@nomad"],
    }),
    package_dir = "/opt/radix/timberland/nomad",
)

pkg_tar(
    name = "timberland-nomad-config",
    srcs = [
        "//timberland/assets:nomad/config/nomad.hcl",
    ],
    package_dir = "/opt/radix/timberland/nomad/config",
)

pkg_tar(
    name = "timberland-systemd-services",
    srcs = [
        "//timberland/assets:systemd/consul.service",
        "//timberland/assets:systemd/consul-template.service",
        "//timberland/assets:systemd/nomad.service",
        "//timberland/assets:systemd/timberland-after-startup.service",
        "//timberland/assets:systemd/timberland-svc.service",
        "//timberland/assets:systemd/vault.service",
    ],
    package_dir = "/etc/systemd/system",
)

pkg_tar(
    name = "timberland-networkd",
    srcs = [
        "//timberland/assets:networkd/10-radix-consul",
        "//timberland/assets:networkd/10-radix-nomad",
    ],
    package_dir = "/etc/networkd-dispatcher/routable.d",
)

timberland_services = [
    "//algs/common/algs_mega_uservice:algs-mega-uservice-terraform",
    "//device_drivers/elemental/gateway:elemental-gateway-bin_deploy.jar",
    "//device_drivers/elemental/kafka_bridge:elemental_bridge-bin_deploy.jar",
    "//device_drivers/eve:eve-bin_deploy.jar",
    "//device_drivers/oceanoptics:oceanoptics-bin_deploy.jar",
    "//device_drivers/hw_discovery:hw-discovery-bin_deploy.jar",
    "//device_drivers/kafka_connectors/elastic:kafka_connect_elastic-bin_deploy.jar",
    "//device_drivers/kafka_connectors/osipi:kafka_connect_osipi-bin_deploy.jar",
    "//device_drivers/kafka_connectors/yugabyte:kafka_connect_yugabyte-bin_deploy.jar",
    "//device_drivers/ln2:ln2-bin_deploy.jar",
    "//device_drivers/messaging:messaging-bin_deploy.jar",
    "//device_drivers/mock_bioreactor:mock-bioreactor-bin_deploy.jar",
    "//device_drivers/minifors2:minifors2-bin_deploy.jar",
    "//device_drivers/multitrons:multitrons-bin_deploy.jar",
    "//device_drivers/olditrons:olditrons-bin_deploy.jar",
    "//device_drivers/eth_multitrons:eth_multitrons-bin_deploy.jar",
    "//device_drivers/opentrons:opentrons-bin_deploy.jar",
    "//device_drivers/quantstudio:quantstudio-bin_deploy.jar",
    "//device_drivers/tecan:tecan-bin_deploy.jar",
    "//device_drivers/ht91100:ht91100-bin_deploy.jar",
    "//device_drivers/tfexactive:tfexactive-bin_deploy.jar",
    "//device_drivers/watlow96_thermal:watlow96_thermal-bin_deploy.jar",
    "//device_drivers/octet:octet-bin_deploy.jar",
    "//runtime:runtime-bootstrap_deploy.jar",
    "//utils/api:api-bin_deploy.jar",
    "//utils/bunny_uservice:bunny-uservice-bin_deploy.jar",
    "//utils/healthcheck:healthcheck-bin_deploy.jar",
    "//utils/rainbowuservice:rainbow-uservice-bin_deploy.jar",
    "//utils/scheduleruservice:scheduler-uservice-bin_deploy.jar",  #TODO confirm this is right
    "//utils/s3lts:syncer-bin_deploy.jar",
    "//vm:vm-bin_deploy.jar",
    "//vm:vm-runner-bin_deploy.jar",
    "//vm:vm-test-runner-bin_deploy.jar",
]

services_pkg_tar(
    name = "timberland-services-proguard",
    srcs = timberland_services,
    package_dir = "/opt/radix/minio_data/service-jars/",
    use_proguard = True,
)

services_pkg_tar(
    name = "timberland-services",
    srcs = timberland_services,
    package_dir = "/opt/radix/minio_data/service-jars/",
    use_proguard = False,
)

pkg_tar(
    name = "timberland-net-utils",
    srcs = [
        "//utils/tcp_tunnel:disco_tunnel_deploy.jar",
    ],
    package_dir = "/opt/radix/networking/",
)

# These jars are distinct from the ones on the devdrivers target above. While the deploy jars above
#   are the main class for starting the monitor actor and triggering the connector to initialize,
#   the final jars below are the ones containing the actual connector code, purged of all duplicate
#   deps
pkg_tar(
    name = "timberland-connectors",
    srcs = [
        "//device_drivers/elemental/kafka_bridge:elemental_bridge-connector-patched.jar",
        "//device_drivers/kafka_connectors/osipi:kafka_connect_osipi-connector-patched.jar",
    ],
    package_dir = "/opt/radix/kafka_connectors/",
)

# pkg_tar(
#     name = "timberland-radix-ui",
#     srcs = [
#         "//interface:fronend-vite",
#     ],
#     package_dir = "/opt/radix/interface/",
# )

pkg_tar(
    name = "timberland-cni-config",
    srcs = [
        "//timberland/assets:container_networking/cni.conf",
    ],
    package_dir = "/etc/sysctl.d/",
)

pkg_tar(
    name = "timberland-windows-startup-script",
    srcs = [
        "//timberland/assets:windows/afterstartup.bat",
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

pkg_tar(
    name = "release-name-tar",
    srcs = [
        "commit-message",
        "release-name",
    ],
    package_dir = "/opt/radix/timberland",
)

#pkg_tar(
#    name = "timberland-tcp-tunnel",
#    srcs = [
#        "//external:jar/net/kanstren/tcptunnel/tcptunnel",
#    ],
#    package_dir = "/opt/radix/timberland",
#)

pkg_tar(
    name = "timberland-avahi",
    srcs = [
        "//timberland/assets:avahi/avahi-autoipd.action",
    ],
    package_dir = "/opt/radix/timberland/avahi",
)

pkg_tar(
    name = "timberland-full-tar-proguard",
    deps = [
        ":timberland-services-proguard",
        ":timberland-slim-tar",
    ],
)

pkg_tar(
    name = "timberland-full-tar",
    deps = [
        # ":timberland-radix-ui",
        ":timberland-services",
        ":timberland-slim-tar",
    ],
)

pkg_tar(
    name = "timberland-slim-tar",
    deps = [
        ":release-name-tar",
        ":terraform-full-deploy",
        ":timberland-avahi",
        ":timberland-cni-config",
        ":timberland-connectors",
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
        ":timberland-net-utils",
        ":timberland-exec",
        ":timberland-networkd",
        ":timberland-nomad",
        ":timberland-nomad-config",
        ":timberland-systemd-services",
        ":timberland-windows-startup-script",
        #        ":timberland-tcp-tunnel",
        ":timberland-vault",
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
    post_install_script = "//timberland/assets:windows/postinst.bat",
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
    postinst = "//timberland/assets:debian/postinst.sh",
    prerm = "//timberland/assets:debian/prerm.sh",
    version = "0.1",  # filename will have a blank version bc file can't be read at analysis time
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
    postinst = "//timberland/assets:debian/postinst.sh",
    prerm = "//timberland/assets:debian/prerm.sh",
    version = "0.1",  # filename will have a blank version bc file can't be read at analysis time
)

rpm_package(
    name = "timberland-rpm",
    data = ":timberland-full-tar",
    rpm_file = "timberland-rpm-all.rpm",
    spec_template = "//timberland/assets:redhat/rpm.spec",
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
)

scala_library(
    name = "synthetic-integration-lib",
    srcs = ["src/test/integration/src/TimberlandIntegration.scala"],
    visibility = ["//visibility:public"],
    exports = [
        "//timberland:timberland-lib",
        "@third_party//3rdparty/jvm/io/7mind/izumi:logstage_core",
        "@third_party//3rdparty/jvm/org/scalatest",
    ],
    deps = [
        "//timberland:timberland-lib",
        "@third_party//3rdparty/jvm/io/7mind/izumi:logstage_core",
        "@third_party//3rdparty/jvm/org/scalatest",
    ],
)

scala_test(
    name = "synthetic-integration",
    srcs = ["src/test/integration/src/TimberlandIntegrationSpec.scala"],
    tags = ["v1integration"],
    #NOTE: use this tag for all the integration tests so that it can be run with --jobs=1,
    #giving both caching and exclusivity
    #this tag should also be added for the normal bazel test to ignore
    deps = [":synthetic-integration-lib"],
)

terraform_module(
    name = "elasticsearch-terraform",
    jobname = "elasticsearch",
    module_spec = [
        'enable = contains(var.feature_flags, "elasticsearch")',
        'source = "/opt/radix/timberland/terraform/modules/elasticsearch"',
        'dev = contains(var.feature_flags, "dev")',
        "test = var.test",
        "namespace = var.namespace",
        "quorum_size = var.elasticsearch_quorum_size",
    ],
    nomad_templates = [
        "//timberland/assets:terraform/elasticsearch/elasticsearch.tmpl",
        "//timberland/assets:terraform/elasticsearch/kibana.tmpl",
    ],
    terraform_files = [
        "//timberland/assets:terraform/elasticsearch/main.tf",
        "//timberland/assets:terraform/elasticsearch/outputs.tf",
        "//timberland/assets:terraform/elasticsearch/variables.tf",
    ],
    deps = [
        ":minio-terraform",
    ],
)

terraform_module(
    name = "nginx-terraform",
    jobname = "nginx",
    module_spec = [
        'source = "/opt/radix/timberland/terraform/modules/nginx"',
        "namespace = var.namespace",
        'dev = contains(var.feature_flags, "dev")',
        'enable = contains(var.feature_flags, "nginx")',
    ],
    terraform_files = [
        "//timberland/assets:terraform/nginx/nginx.tmpl",
        "//timberland/assets:terraform/nginx/main.tf",
        "//timberland/assets:terraform/nginx/outputs.tf",
        "//timberland/assets:terraform/nginx/variables.tf",
    ],
    deps = [
        ":minio-terraform",
    ],
)

terraform_module(
    name = "kafka-terraform",
    jobname = "kafka",
    module_spec = [
        'enable = contains(var.feature_flags, "kafka")',
        'source = "/opt/radix/timberland/terraform/modules/kafka"',
        "test = var.test",
        "namespace = var.namespace",
        'dev = contains(var.feature_flags, "dev")',
        "quorum_size = var.kafka_quorum_size",
        "zookeeper_quorum_size = var.zookeeper_quorum_size",
        "interbroker_port = var.kafka_interbroker_port",
    ],
    nomad_template = "//timberland/assets:terraform/kafka/kafka.tmpl",
    output_deps = [
        ":zookeeper-terraform",
    ],
    terraform_files = [
        "//timberland/assets:terraform/kafka/main.tf",
        "//timberland/assets:terraform/kafka/outputs.tf",
        "//timberland/assets:terraform/kafka/variables.tf",
    ],
)

terraform_module(
    name = "kafka_companions-terraform",
    jobname = "kafka_companions",
    module_spec = [
        'enable = contains(var.feature_flags, "kafka_companions")',
        'source = "/opt/radix/timberland/terraform/modules/kafka_companions"',
        'dev = contains(var.feature_flags, "dev")',
        "test = var.test",
        "namespace = var.namespace",
        "quorum_size = var.kafka_quorum_size",
        "interbroker_port = var.kafka_interbroker_port",
    ],
    nomad_template = "//timberland/assets:terraform/kafka_companions/kafka_companions.tmpl",
    output_deps = [
        ":kafka-terraform",
    ],
    terraform_files = [
        "//timberland/assets:terraform/kafka_companions/main.tf",
        "//timberland/assets:terraform/kafka_companions/outputs.tf",
        "//timberland/assets:terraform/kafka_companions/variables.tf",
    ],
    deps = [
        ":minio-terraform",
    ],
)

terraform_module(
    name = "minio-terraform",
    jobname = "minio",
    module_spec = [
        'enable = contains(var.feature_flags, "minio")',
        'source = "/opt/radix/timberland/terraform/modules/minio"',
        "namespace = var.namespace",
        "test = var.test",
    ],
    nomad_template = "//timberland/assets:terraform/minio/minio.tmpl",
    terraform_files = [
        "//timberland/assets:terraform/minio/main.tf",
        "//timberland/assets:terraform/minio/outputs.tf",
        "//timberland/assets:terraform/minio/variables.tf",
    ],
    deps = [
        ":kafka-terraform",
    ],
)

terraform_module(
    name = "yugabyte-terraform",
    jobname = "yugabyte",
    module_spec = [
        'enable = contains(var.feature_flags, "yugabyte")',
        'source = "/opt/radix/timberland/terraform/modules/yugabyte"',
        'dev = contains(var.feature_flags, "dev")',
        "namespace = var.namespace",
        "test = var.test",
        "quorum_size = var.yugabyte_quorum_size",
    ],
    nomad_templates = [
        "//timberland/assets:terraform/yugabyte/yb_masters.tmpl",
        "//timberland/assets:terraform/yugabyte/yb_tservers.tmpl",
    ],
    terraform_files = [
        "//timberland/assets:terraform/yugabyte/main.tf",
        "//timberland/assets:terraform/yugabyte/outputs.tf",
        "//timberland/assets:terraform/yugabyte/variables.tf",
    ],
    deps = [
        ":minio-terraform",
    ],
)

terraform_module(
    name = "zookeeper-terraform",
    jobname = "zookeeper",
    module_spec = [
        'enable = contains(var.feature_flags, "zookeeper")',
        'source = "/opt/radix/timberland/terraform/modules/zookeeper"',
        'dev = contains(var.feature_flags, "dev")',
        "test = var.test",
        "namespace = var.namespace",
        "quorum_size = var.zookeeper_quorum_size",
    ],
    nomad_template = "//timberland/assets:terraform/zookeeper/zookeeper.tmpl",
    terraform_files = [
        "//timberland/assets:terraform/zookeeper/main.tf",
        "//timberland/assets:terraform/zookeeper/outputs.tf",
        "//timberland/assets:terraform/zookeeper/variables.tf",
    ],
)

terraform_provider(
    name = "terraform-consul",
    source = select({
        "aarch64": "@terraform-provider-consul_arm//file",
        "windows": "@terraform-provider-consul_win//file",
        "//conditions:default": "@terraform-provider-consul//file",
    }),
    spec = """provider "consul" {
  address = "https://${var.consul_address}:8501"
  ca_file = var.tls_ca_file
  cert_file = var.tls_cert_file
  key_file = var.tls_key_file
  token = var.acl_token
}
""",
)

terraform_provider(
    name = "terraform-nomad",
    #    source = "@terraform-provider-nomad//file",
    source = select({
        "aarch64": "@terraform-provider-nomad_arm//file",
        "windows": "@terraform-provider-nomad_win//file",
        "//conditions:default": "@terraform-provider-nomad//file",
        #        "//conditions:default": "//containers:nomad-tf-provider-builder/terraform-provider-nomad_1.4.9_linux_amd64.zip",
    }),
    spec = """provider "nomad" {
   address = "https://${var.nomad_address}:4646"
   ca_file = var.tls_ca_file
   cert_file = var.tls_nomad_cert_file
   key_file = var.tls_nomad_key_file
   secret_id = var.acl_token
}
""",
)

terraform_provider(
    name = "terraform-vault",
    #    source = "@terraform-provider-vault//file",
    source = select({
        "aarch64": "@terraform-provider-vault_arm//file",
        "windows": "@terraform-provider-vault_win//file",
        "//conditions:default": "@terraform-provider-vault//file",
    }),
    spec = """provider "vault" {
    address = "https://${var.vault_address}:8200"
    ca_cert_file = var.tls_ca_file
    token = var.vault_token
}
""",
)

terraform_deployment(
    name = "terraform-full-deploy",
    module_dir = "/opt/radix/timberland/terraform",
    plugin_sources = [
        ":terraform-consul",
        ":terraform-nomad",
        ":terraform-vault",
    ],
    resources = [
        ":elasticsearch-terraform",
        ":kafka-terraform",
        ":kafka_companions-terraform",
        ":minio-terraform",
        ":nginx-terraform",
        ":yugabyte-terraform",
        ":zookeeper-terraform",
        "//algs/common/algs_mega_uservice:algs-mega-uservice-terraform",
        "//device_drivers/elemental/kafka_bridge:elemental_bridge-terraform",
        "//device_drivers/eth_multitrons:eth_multitrons-terraform",
        "//device_drivers/ht91100:ht91100-terraform",
        "//device_drivers/hw_discovery:hw_discovery-terraform",
        "//device_drivers/kafka_connectors/elastic:kafka_connect_elastic-terraform",
        "//device_drivers/kafka_connectors/osipi:kafka_connect_osipi-terraform",
        "//device_drivers/kafka_connectors/yugabyte:kafka_connect_yugabyte-terraform",
        "//device_drivers/ln2:ln2-terraform",
        "//device_drivers/messaging:apprise-terraform",
        "//device_drivers/minifors2:minifors2-terraform",
        "//device_drivers/mock_bioreactor:mock-bioreactor-terraform",
        "//device_drivers/multitrons:multitrons-terraform",
        "//device_drivers/oceanoptics:oceanoptics-terraform",
        "//device_drivers/octet:octet-terraform",
        "//device_drivers/opentrons:opentrons-terraform",
        "//device_drivers/quantstudio:quantstudio-terraform",
        "//device_drivers/tecan:tecan-terraform",
        "//device_drivers/tfexactive:tfexactive-terraform",
        "//device_drivers/watlow96_thermal:watlow96_thermal-terraform",
        "//runtime:runtime-terraform",
        "//utils/api:web-interface",
        "//utils/bunny_uservice:bunny_uservice-terraform",
        "//utils/healthcheck:healthcheck-terraform",
        "//utils/rainbowuservice:rainbow-terraform",
        "//utils/s3lts:s3lts-terraform",
        "//utils/scheduleruservice:scheduler-terraform",  #TODO confirm this is right
        "//vm:vm-runner-terraform",
        "//vm:vm-terraform",
        "//vm:vm-test-runner-terraform",
    ],
    terraform_source = select({
        "aarch64": ["@terraform_arm//:terraform"],
        "windows": ["@terraform_win//:terraform.exe"],
        "//conditions:default": ["@terraform"],
    }),
    toplevel_module_aux = [
        "//timberland/assets:terraform/main/variables.tf",
        "//timberland/assets:terraform/main/outputs.tf",
    ],
)

scala_binary(
    name = "debug",
    srcs = [],
    main_class = "ammonite.Main",
    deps = [
        ":timberland-lib",
        "@third_party//3rdparty/jvm/com/lihaoyi:ammonite_2_13_3",
    ],
)
