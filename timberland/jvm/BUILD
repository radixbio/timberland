load("@rules_pkg//:pkg.bzl", "pkg_deb", "pkg_tar")
load("@io_bazel_rules_docker//docker/util:run.bzl", "container_run_and_commit", "container_run_and_extract")
load("@io_bazel_rules_docker//container:container.bzl", "container_image", "container_layer", "container_load", "container_push")
load("//tools:pkg_rpm_with_branch.bzl", "pkg_rpm_with_branch")
load("//tools:terraform.bzl", "dynamic_rpm_spec", "terraform_deployment", "terraform_module", "terraform_provider")
load("//tools:write_git_branch_to_file.bzl", "write_git_branch_to_file")

package(
    default_visibility = ["//timberland:__subpackages__"],
)

scala_library(
    name = "timberland-dependencies",
    exports = [
        "//shared/shared/shared/shared:shared-shared",
        "//utils/helm:helm-jvm",
        "@third_party//3rdparty/jvm/com/github/oshi:oshi_core",
        "@third_party//3rdparty/jvm/com/github/xuwei_k:optparse_applicative",
        "@third_party//3rdparty/jvm/com/lihaoyi:ammonite_ops",
        "@third_party//3rdparty/jvm/com/outr:scribe",
        "@third_party//3rdparty/jvm/dnsjava",
        "@third_party//3rdparty/jvm/org/fusesource/jansi",
        "@third_party//3rdparty/jvm/org/scala_lang/modules:scala_parser_combinators",
    ],
    deps = [
        "//shared/shared/shared/shared:shared-shared",
        "//utils/helm:helm-jvm",
        "@third_party//3rdparty/jvm/com/github/oshi:oshi_core",
        "@third_party//3rdparty/jvm/com/github/xuwei_k:optparse_applicative",
        "@third_party//3rdparty/jvm/com/lihaoyi:ammonite_ops",
        "@third_party//3rdparty/jvm/com/outr:scribe",
        "@third_party//3rdparty/jvm/org/fusesource/jansi",
        "@third_party//3rdparty/jvm/org/scala_lang/modules:scala_parser_combinators",
        "@third_party//3rdparty/jvm/org/slf4j:slf4j_nop",
    ],
)

#TODO @sibblegp make this go away
#it's shared dependencies that timberland the commandline tool
#has with the in-container process
timberland_core_srcs = [
    "src/main/scala/com/radix/timberland/defns.scala",
    "src/main/scala/com/radix/timberland/launch/consulutil.scala",
    "src/main/scala/com/radix/timberland/launch/daemonutil.scala",
    "src/main/scala/com/radix/timberland/runtime/auth.scala",
    "src/main/scala/com/radix/timberland/flags/config.scala",
    "src/main/scala/com/radix/timberland/flags/featureFlags.scala",
    "src/main/scala/com/radix/timberland/flags/flagConfiguration.scala",
    "src/main/scala/com/radix/timberland/runtime/install.scala",
    "src/main/scala/com/radix/timberland/runtime/services.scala",
    "src/main/scala/com/radix/timberland/util/VaultStarter.scala",
    "src/main/scala/com/radix/timberland/util/LogTUI.scala",
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
        "src/main/resources/application.conf",
    ]),
    visibility = ["//visibility:public"],
    exports = [":timberland-dependencies"],
    deps = [
        ":timberland-dependencies",
    ],
)

scala_library(
    name = "timberland-update-lib",
    srcs = ["src/main/scala/com/radix/timberland/util/UpdateModules.scala"],
    exports = [
        ":timberland-core-lib",
        ":timberland-dependencies",
        ":timberland-launcher-lib",
        "@third_party//3rdparty/jvm/io/circe:circe_core",
        "@third_party//3rdparty/jvm/io/circe:circe_derivation",
        "@third_party//3rdparty/jvm/io/circe:circe_generic_extras",
        "@third_party//3rdparty/jvm/io/circe:circe_literal",
        "@third_party//3rdparty/jvm/org/apache/commons:commons_compress",
        "@third_party//3rdparty/jvm/org/http4s:http4s_core",
    ],
    deps = [
        ":timberland-core-lib",
        ":timberland-dependencies",
        ":timberland-launcher-lib",
        "@third_party//3rdparty/jvm/io/circe:circe_core",
        "@third_party//3rdparty/jvm/io/circe:circe_derivation",
        "@third_party//3rdparty/jvm/io/circe:circe_generic_extras",
        "@third_party//3rdparty/jvm/io/circe:circe_literal",
        "@third_party//3rdparty/jvm/org/apache/commons:commons_compress",
        "@third_party//3rdparty/jvm/org/http4s:http4s_core",
    ],
)

scala_binary(
    name = "timberland-update-test",
    main_class = "com.radix.timberland.util.Test",  # TODO for testing only, remove
    deps = [":timberland-update-lib"],
)

scala_library(
    name = "timberland-lib",
    # Exclude the kafka and zookeeper sources
    srcs = glob(
        [
            "src/main/scala/**/*.scala",
        ],
        exclude = timberland_launcher_srcs + timberland_core_srcs,
    ),
    scalacopts = ["-Ypartial-unification"],
    visibility = [
        "//timberland:__subpackages__",
    ],
    exports = [
        ":timberland-core-lib",
        "//shared/shared/shared/shared:shared-shared",
        "//utils/helm:helm-jvm",
        "//utils/sheets/jvm/src/main/scala/com/radix/util/sheets:sheets-interps",  #TODO remove this
        "@third_party//3rdparty/jvm/com/github/xuwei_k:optparse_applicative",
        "@third_party//3rdparty/jvm/com/lihaoyi:ammonite_ops",
        "@third_party//3rdparty/jvm/com/outr:scribe",
    ],
    deps = [
        ":timberland-core-lib",
        ":timberland-update-lib",
        "//utils/helm:helm-jvm",
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
    name = "timberland-test",
    srcs = ["src/main/scala/com/radix/timberland/runner.scala"],
    main_class = "com.radix.timberland.TestApp",
    deps = [":timberland-lib"],
)

filegroup(
    name = "timberland-filegroup",
    srcs = [":timberland-bin_deploy.jar"],
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
    strip_prefix = "/",
    deps = ["//device_drivers/hw_discovery/jvm:disco-deploy"],
)

pkg_tar(
    name = "timberland-vault",
    srcs = [
        "//timberland/assets:systemd/vault.env.conf",
        "//timberland/assets:vault/nomad-cluster-role.json",
        "//timberland/assets:vault/nomad-server-policy.hcl",
        "//timberland/assets:vault/read-consul-ui-policy.hcl",
        "//timberland/assets:vault/read-flag-config-policy.hcl",
        "//timberland/assets:vault/remote-access-policy.hcl",
        "//timberland/assets:vault/vault_config.conf",
        "@vault",
        "@vault-plugin-secrets-oauthapp",
    ],
    package_dir = "/opt/radix/timberland/vault",
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

pkg_tar(
    name = "timberland-consul",
    srcs = [
        "//timberland/assets:consul/default-policy.hcl",
        "//timberland/assets:systemd/consul.env.conf",
        "@consul",
    ],
    package_dir = "/opt/radix/timberland/consul",
)

pkg_tar(
    name = "timberland-consul-config",
    srcs = [
        "//timberland/assets:consul/consul.json",
    ],
    package_dir = "/opt/radix/timberland/consul/config",
)

pkg_tar(
    name = "timberland-nomad",
    srcs = [
        "//timberland/assets:systemd/nomad.env.conf",
        "@nomad",
    ],
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
    name = "timberland-nomad-config-elasticsearch",
    srcs = [
        "//timberland/assets:nomad/config/elasticsearch/unicast_hosts.tpl",
    ],
    package_dir = "/opt/radix/timberland/nomad/config/elasticsearch",
)

pkg_tar(
    name = "timberland-nomad-config-zookeeper",
    srcs = [
        "//timberland/assets:nomad/config/zookeeper/config/zoo.cfg",
        "//timberland/assets:nomad/config/zookeeper/config/zoo.tpl",
        "//timberland/assets:nomad/config/zookeeper/config/zoo_replicated.cfg.dynamic",
    ],
    package_dir = "/opt/radix/timberland/nomad/zookeeper",
)

pkg_tar(
    name = "timberland-systemd-services",
    srcs = [
        "//timberland/assets:systemd/consul.service",
        "//timberland/assets:systemd/nomad.service",
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

pkg_tar(
    name = "timberland-devdrivers",
    srcs = [
        "//device_drivers/hw_discovery/jvm:hw-discovery-bin_deploy.jar",
        "//device_drivers/ln2/jvm:ln2-bin_deploy.jar",
        "//device_drivers/opentrons/jvm:opentrons-bin_deploy.jar",
        "//device_drivers/tf-exactive/jvm:tfexactive-bin",
        "//device_drivers/tf-exactive/jvm:tfexactive-bin.jar",
    ],
    package_dir = "/opt/radix/device_drivers/",
)

write_git_branch_to_file(
    name = "git-branch-file",
    package_dir = "/opt/radix/timberland",
)

pkg_tar(
    name = "timberland-full-tar",
    deps = [
        ":git-branch-file",
        ":terraform-full-deploy",
        ":terraform-update-config",
        ":timberland-consul",
        ":timberland-consul-config",
        ":timberland-devdrivers",
        ":timberland-exec",
        ":timberland-networkd",
        ":timberland-nomad",
        ":timberland-nomad-config",
        ":timberland-nomad-config-elasticsearch",
        ":timberland-nomad-config-zookeeper",
        ":timberland-systemd-services",
        ":timberland-vault",
    ],
)

pkg_deb(
    name = "timberland-deb",
    architecture = "amd64",
    data = ":timberland-full-tar",
    depends = [
        "docker.io | docker-ce",
        "openjdk-11-jdk",
        "net-tools",
        "avahi-utils",
    ],
    description = "Radix Timberland deploy file",
    maintainer = "Alex Hulbert",
    package = "radix-timberland",
    postinst = "//timberland/assets:debian/postinst.sh",
    prerm = "//timberland/assets:debian/prerm.sh",
    version = "0.1",
)

dynamic_rpm_spec(
    name = "rpm_custom_spec",
    base_spec = "//timberland/assets:redhat/rpm.spec",
    tar_archive = ":timberland-full-tar",
)

pkg_rpm_with_branch(
    name = "timberland-rpm",
    architecture = "amd64",
    data = [":timberland-full-tar"],
    release = "1",
    spec_file = ":rpm_custom_spec",
)

sh_binary(
    name = "timberland-ec2-dev",
    srcs = [
        "src/test/ci.sh",
    ],
    args = [
        "persist",
    ],
    data = [
        "src/test/install-deb.sh",
        "src/test/install-rpm.sh",
        "src/test/service_test.py",
        ":radix-timberland_0.1_amd64.deb",
        ":timberland-rpm-amd64.rpm",
    ],
    #tags = ["no-sandbox"],
)

sh_test(
    name = "timberland-ci",
    size = "large",
    timeout = "eternal",
    srcs = [
        "src/test/ci.sh",
    ],
    data = [
        "src/test/install-deb.sh",
        "src/test/install-rpm.sh",
        "src/test/service_test.py",
        ":radix-timberland_0.1_amd64.deb",
        ":timberland-rpm-amd64.rpm",
    ],
    tags = ["no-sandbox"],
)

scala_library(
    name = "synthetic-integration-lib",
    srcs = ["src/test/integration/src/integration.scala"],
    visibility = ["//visibility:public"],
    exports = [
        "//timberland/jvm:timberland-lib",
        "@third_party//3rdparty/jvm/io/7mind/izumi:logstage_core",
        "@third_party//3rdparty/jvm/org/scalatest",
    ],
    deps = [
        "//timberland/jvm:timberland-lib",
        "@third_party//3rdparty/jvm/io/7mind/izumi:logstage_core",
        "@third_party//3rdparty/jvm/org/scalatest",
    ],
)

scala_test(
    name = "synthetic-integration",
    srcs = ["src/test/integration/src/startup.scala"],
    tags = ["v1integration"],
    #NOTE: use this tag for all the integration tests so that it can be run with --jobs=1,
    #giving both caching and exclusivity
    #this tag should also be added for the normal bazel test to ignore
    deps = [":synthetic-integration-lib"],
)

pkg_tar(
    name = "terraform-update-config",
    srcs = [
        "//timberland/assets:update/cloud_upload_config.sh",
    ],
    package_dir = "/opt/radix/timberland/terraform",
)

terraform_module(
    name = "apprise-terraform",
    file_dir = "//timberland/assets:terraform/apprise",
    jobname = "apprise",
    module_spec = [
        'enable = contains(var.feature_flags, "apprise")',
        'source = "/opt/radix/timberland/terraform/modules/apprise"',
        "test = var.test",
        "prefix = var.prefix",
    ],
)

terraform_module(
    name = "elasticsearch-terraform",
    file_dir = "//timberland/assets:terraform/elasticsearch",
    jobname = "elasticsearch",
    module_spec = [
        'enable = contains(var.feature_flags, "elasticsearch")',
        'source = "/opt/radix/timberland/terraform/modules/elasticsearch"',
        'dev = contains(var.feature_flags, "dev")',
        "test = var.test",
        "prefix = var.prefix",
    ],
)

terraform_module(
    name = "elemental-terraform",
    file_dir = "//timberland/assets:terraform/elemental",
    jobname = "elemental",
    module_spec = [
        'enable = contains(var.feature_flags, "elemental")',
        'source = "/opt/radix/timberland/terraform/modules/elemental"',
        'dev = contains(var.feature_flags, "dev")',
        "test = var.test",
        "prefix = var.prefix",
    ],
    deps = [":kafka_companions-terraform"],
)

terraform_module(
    name = "es_kafka_connector-terraform",
    file_dir = "//timberland/assets:terraform/es_kafka_connector",
    jobname = "es_kafka_connector",
    module_spec = [
        'enable = contains(var.feature_flags, "es_kafka_connector")',
        'source = "/opt/radix/timberland/terraform/modules/es_kafka_connector"',
        "test = var.test",
        "prefix = var.prefix",
        "schema_registry_address = module.kafka_companions.schema_registry_health_result",
        "elasticsearch_address = module.elasticsearch.elasticsearch_health_result",
        "connect_address = module.kafka_companions.connect_health_result",
    ],
    deps = [
        ":elasticsearch-terraform",
        ":kafka_companions-terraform",
    ],
)

terraform_module(
    name = "kafka-terraform",
    file_dir = "//timberland/assets:terraform/kafka",
    jobname = "kafka",
    module_spec = [
        'enable = contains(var.feature_flags, "kafka")',
        'source = "/opt/radix/timberland/terraform/modules/kafka"',
        "test = var.test",
        "prefix = var.prefix",
        "quorum_size = var.kafka_quorum_size",
        "interbroker_port = var.kafka_interbroker_port",
        "zookeeper_address = module.zookeeper.zookeeper_health_result",
    ],
    deps = [":zookeeper-terraform"],
)

terraform_module(
    name = "kafka_companions-terraform",
    file_dir = "//timberland/assets:terraform/kafka_companions",
    jobname = "kafka_companions",
    module_spec = [
        'enable = contains(var.feature_flags, "kafka_companions")',
        'source = "/opt/radix/timberland/terraform/modules/kafka_companions"',
        'dev = contains(var.feature_flags, "dev")',
        "test = var.test",
        "prefix = var.prefix",
        "quorum_size = var.kafka_companions_quorum_size",
        "interbroker_port = var.kafka_interbroker_port",
        "kafka_address = module.kafka.kafka_health_result",
    ],
    deps = [":kafka-terraform"],
)

terraform_module(
    name = "minio-terraform",
    file_dir = "//timberland/assets:terraform/minio",
    jobname = "minio",
    module_spec = [
        'enable = contains(var.feature_flags, "minio")',
        'source = "/opt/radix/timberland/terraform/modules/minio"',
        "prefix = var.prefix",
        "test = var.test",
        "kafka_address = module.kafka.kafka_health_result",
        "have_upstream_creds = contains(var.defined_config_vars, \"minio.aws_access_key_id\") && contains(var.defined_config_vars, \"minio.aws_secret_access_key\")"
    ],
    deps = [":kafka-terraform"],
)

terraform_module(
    name = "retool-terraform",
    file_dir = "//timberland/assets:terraform/retool",
    jobname = "retool",
    module_spec = [
        'enable = contains(var.feature_flags, "retool_postgres")',
        'source = "/opt/radix/timberland/terraform/modules/retool"',
        'dev = contains(var.feature_flags, "dev")',
        "test = var.test",
        "prefix = var.prefix",
    ],
)

terraform_module(
    name = "retool_pg_kafka_connector-terraform",
    file_dir = "//timberland/assets:terraform/retool_pg_kafka_connector",
    jobname = "retool_pg_kafka_connector",
    module_spec = [
        'enable = contains(var.feature_flags, "retool_pg_kafka_connector")',
        'source = "/opt/radix/timberland/terraform/modules/retool_pg_kafka_connector"',
        "test = var.test",
        "prefix = var.prefix",
        "schema_registry_address = module.kafka_companions.schema_registry_health_result",
        "connect_address = module.kafka_companions.connect_health_result",
        "postgres_address = module.retool.postgres_health_result",
    ],
    deps = [
        ":kafka_companions-terraform",
        ":retool-terraform",
    ],
)

terraform_module(
    name = "yugabyte-terraform",
    file_dir = "//timberland/assets:terraform/yugabyte",
    jobname = "yugabyte",
    module_spec = [
        'enable = contains(var.feature_flags, "yugabyte")',
        'source = "/opt/radix/timberland/terraform/modules/yugabyte"',
        'dev = contains(var.feature_flags, "dev")',
        "prefix = var.prefix",
        "test = var.test",
        "quorum_size = var.yugabyte_quorum_size",
    ],
    deps = [],
)

terraform_module(
    name = "yugabyte_kafka_connector-terraform",
    file_dir = "//timberland/assets:terraform/yugabyte_kafka_connector",
    jobname = "yugabyte_kafka_connector",
    module_spec = [
        'enable = contains(var.feature_flags, "yb_kafka_connector")',
        'source = "/opt/radix/timberland/terraform/modules/yugabyte_kafka_connector"',
        "test = var.test",
        "prefix = var.prefix",
        "schema_registry_address = module.kafka_companions.schema_registry_health_result",
        "connect_address = module.kafka_companions.connect_health_result",
        "yb_tserver_address = module.yugabyte.yb_tserver_health_result",
    ],
    deps = [
        ":kafka_companions-terraform",
        ":yugabyte-terraform",
    ],
)

terraform_module(
    name = "zookeeper-terraform",
    file_dir = "//timberland/assets:terraform/zookeeper",
    jobname = "zookeeper",
    module_spec = [
        'enable = contains(var.feature_flags, "zookeeper")',
        'source = "/opt/radix/timberland/terraform/modules/zookeeper"',
        'dev = contains(var.feature_flags, "dev")',
        "test = var.test",
        "prefix = var.prefix",
        "quorum_size = var.zookeeper_quorum_size",
    ],
    deps = [],
)

terraform_module(
    name = "s3lts-terraform",
    file_dir = "//timberland/assets:terraform/s3lts",
    jobname = "s3lts",
    module_spec = [
        'enable = contains(var.feature_flags, "s3lts")',
        'source = "/opt/radix/timberland/terraform/modules/s3lts"',
        "test = var.test",
        "prefix = var.prefix",
        "runtime_address = module.runtime.runtime_health_result",
        "minio_address = module.minio.minio_health_result",
        "schema_registry_address = module.kafka_companions.schema_registry_health_result"
    ],
    deps = [
        ":kafka_companions-terraform",
        ":minio-terraform",
        ":runtime-terraform",
    ],
)

terraform_module(
    name = "runtime-terraform",
    file_dir = "//timberland/assets:terraform/runtime",
    jobname = "runtime",
    module_spec = [
        'enable = contains(var.feature_flags, "runtime")',
        'source = "/opt/radix/timberland/terraform/modules/runtime"',
        "test = var.test",
        "prefix = var.prefix",
    ],
    deps = [
        ":kafka-terraform",
    ],
)
#
#terraform_module(
#    name = "main-terraform",
#    file_dir = "//timberland/assets:terraform/main",
#    jobname = "main",
#)

terraform_provider(
    name = "terraform-consul",
    source = "@terraform-provider-consul//file",
    spec = """provider "consul" {
  address = "http://${var.consul_address}:8500"
  version = "~> 2.7"
  token = var.acl_token
}
""",
)

terraform_provider(
    name = "terraform-nomad",
    source = "@terraform-provider-nomad//file",
    spec = """provider "nomad" {
   address = "http://${var.nomad_address}:4646"
   version = "~> 1.4"
   secret_id = var.acl_token
}
""",
)

terraform_provider(
    name = "terraform-vault",
    source = "@terraform-provider-vault//file",
    spec = """provider "vault" {
address = "http://${var.vault_address}:8200"
version = "2.11.0"
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
        ":apprise-terraform",
        ":elasticsearch-terraform",
        ":elemental-terraform",
        ":es_kafka_connector-terraform",
        ":kafka-terraform",
        ":kafka_companions-terraform",
        ":minio-terraform",
        ":retool-terraform",
        ":retool_pg_kafka_connector-terraform",
        ":runtime-terraform",
        ":s3lts-terraform",
        ":yugabyte-terraform",
        ":yugabyte_kafka_connector-terraform",
        ":zookeeper-terraform",
    ],
    terraform_source = "@terraform",
    toplevel_module_aux = [
        "//timberland/assets:terraform/main/variables.tf",
        "//timberland/assets:terraform/main/outputs.tf",
    ],
)
