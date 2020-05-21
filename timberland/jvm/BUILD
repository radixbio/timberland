load(
    "@io_bazel_rules_scala//scala:scala.bzl",
    "scala_binary",
    "scala_library",
    #    "scala_library_suite",
    "scala_macro_library",
    "scala_test",
)
load("@rules_pkg//:pkg.bzl", "pkg_deb", "pkg_tar")
load("@io_bazel_rules_docker//docker/util:run.bzl", "container_run_and_commit", "container_run_and_extract")
load("@io_bazel_rules_docker//container:container.bzl", "container_image", "container_layer", "container_load", "container_push")
load("//:tools/pkg_rpm_with_branch.bzl", "pkg_rpm_with_branch")

package(
    default_visibility = ["//timberland:__subpackages__"],
)

scala_library(
    name = "timberland-dependencies",
    exports = [
        "//3rdparty/jvm/com/github/xuwei_k:optparse_applicative",
        "//3rdparty/jvm/com/lihaoyi:ammonite_ops",
        "//3rdparty/jvm/com/outr:scribe",
        "//3rdparty/jvm/dnsjava",
        "//3rdparty/jvm/org/scala_lang/modules:scala_parser_combinators",
        "//shared/shared/shared/shared:shared-shared",
        "//utils/helm:helm-jvm",
    ],
    deps = [
        "//3rdparty/jvm/com/github/xuwei_k:optparse_applicative",
        "//3rdparty/jvm/com/lihaoyi:ammonite_ops",
        "//3rdparty/jvm/com/outr:scribe",
        "//3rdparty/jvm/org/scala_lang/modules:scala_parser_combinators",
        "//shared/shared/shared/shared:shared-shared",
        "//utils/helm:helm-jvm",
    ],
)

#TODO @sibblegp make this go away
#it's shared dependencies that timberland the commandline tool
#has with the in-container process
timberland_core_srcs = [
    "src/main/scala/com/radix/timberland/defns.scala",
    "src/main/scala/com/radix/timberland/launch/consulutil.scala",
    "src/main/scala/com/radix/timberland/runtime/install.scala",
    "src/main/scala/com/radix/timberland/runtime/services.scala",
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
    exports = [":timberland-dependencies"],
    deps = [":timberland-dependencies"],
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
    visibility = [
        "//timberland:__subpackages__",
    ],
    exports = [
        ":timberland-core-lib",
        "//3rdparty/jvm/com/github/xuwei_k:optparse_applicative",
        "//3rdparty/jvm/com/lihaoyi:ammonite_ops",
        "//3rdparty/jvm/com/outr:scribe",
        #        "//3rdparty/jvm/org/scala_lang/modules:scala_parser_combinators",
        "//shared/shared/shared/shared:shared-shared",
        "//utils/helm:helm-jvm",
        "//utils/helm/core/shared:helm-elemental",
        "//utils/sheets/jvm/src/main/scala/com/radix/util/sheets:sheets-interps",  #TODO remove this
    ],
    deps = [
        ":timberland-core-lib",
        "//utils/helm/core/shared:helm-elemental",
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
        "//timberland/assets:consul/consul.json",
        "//timberland/assets:systemd/consul.env.conf",
        "@consul",
    ],
    package_dir = "/opt/radix/timberland/consul",
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
    name = "timberland-terraform",
    srcs = [
        "@terraform",
    ],
    package_dir = "/opt/radix/timberland/terraform",
)

pkg_tar(
    name = "timberland-terraform-plugins",
    srcs = [
        "@terraform-provider-consul//:terraform-provider-consul_v2.7.0_x4",
        "@terraform-provider-nomad//:terraform-provider-nomad_v1.4.5_x4",
        "@terraform-provider-null//:terraform-provider-null_v2.1.2_x4",
    ],
    package_dir = "/opt/radix/timberland/terraform/plugins",
)

pkg_tar(
    name = "timberland-terraform-module",
    srcs = [
        "//timberland/assets:terraform/main/main.tf",
        "//timberland/assets:terraform/main/variables.tf",
    ],
    package_dir = "/opt/radix/timberland/terraform/main",
)

pkg_tar(
    name = "timberland-terraform-module-templates",
    srcs = [
        "//timberland/assets:terraform/main/templates/apprise.tmpl",
        "//timberland/assets:terraform/main/templates/elastic_search.tmpl",
        "//timberland/assets:terraform/main/templates/elemental.tmpl",
        "//timberland/assets:terraform/main/templates/es_kafka_connector.tmpl",
        "//timberland/assets:terraform/main/templates/kafka.tmpl",
        "//timberland/assets:terraform/main/templates/kafka_companions.tmpl",
        "//timberland/assets:terraform/main/templates/minio.tmpl",
        "//timberland/assets:terraform/main/templates/retool.tmpl",
        "//timberland/assets:terraform/main/templates/retool_pg_kafka_connector.tmpl",
        "//timberland/assets:terraform/main/templates/vault.tmpl",
        "//timberland/assets:terraform/main/templates/yugabyte.tmpl",
        "//timberland/assets:terraform/main/templates/yugabyte_kafka_connector.tmpl",
        "//timberland/assets:terraform/main/templates/zookeeper.tmpl",
    ],
    package_dir = "/opt/radix/timberland/terraform/main/templates",
)

pkg_tar(
    name = "timberland-nomad-config",
    srcs = [
        "//timberland/assets:nomad/config/nomad.hcl",
    ],
    package_dir = "/opt/radix/timberland/nomad/config",
)

pkg_tar(
    name = "timberland-nomad-connect",
    srcs = [
        "//timberland/assets:nomad/connect/postgres_source.sh",
        "//timberland/assets:nomad/connect/start.sh",
        "//timberland/assets:nomad/connect/yugabyte_sink.sh",
    ],
    package_dir = "/opt/radix/timberland/nomad/connect",
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
    ],
    package_dir = "/etc/systemd/system",
)

pkg_tar(
    name = "timberland-systemd-networks",
    srcs = [
        "//timberland/assets:systemd/dummy0.netdev",
        "//timberland/assets:systemd/dummy0.network",
    ],
    package_dir = "/etc/systemd/network",
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
    name = "timberland-nginx",
    srcs = [
        "//timberland/assets:nginx/nginx-minio-noupstream.conf",
        "//timberland/assets:nginx/nginx-minios.conf",
        "//timberland/assets:nginx/nginx-retool.conf",
    ],
    package_dir = "/opt/radix/timberland/nginx",
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

pkg_tar(
    name = "timberland-full-tar",
    deps = [
        ":timberland-consul",
        ":timberland-devdrivers",
        ":timberland-exec",
        ":timberland-networkd",
        ":timberland-nginx",
        ":timberland-nomad",
        ":timberland-nomad-config",
        ":timberland-nomad-config-elasticsearch",
        ":timberland-nomad-config-zookeeper",
        ":timberland-nomad-connect",
        ":timberland-systemd-networks",
        ":timberland-systemd-services",
        ":timberland-terraform",
        ":timberland-terraform-module",
        ":timberland-terraform-module-templates",
        ":timberland-terraform-plugins",
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
    ],
    description = "Radix Timberland deploy file",
    maintainer = "Alex Wied",
    package = "radix-timberland",
    postinst = "//timberland/assets:debian/postinst.sh",
    version = "0.1",
)

pkg_rpm_with_branch(
    name = "timberland-rpm",
    architecture = "amd64",
    data = [":timberland-full-tar"],
    release = "1",
    spec_file = "//timberland/assets:redhat/rpm.spec",
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
        "src/test/service_test.py",
        ":radix-timberland_0.1_amd64.deb",
    ],
    tags = ["no-sandbox"],
)

sh_test(
    name = "timberland-ci",
    size = "large",
    srcs = [
        "src/test/ci.sh",
    ],
    data = [
        "src/test/service_test.py",
        ":radix-timberland_0.1_amd64.deb",
    ],
    tags = ["no-sandbox"],
)

container_layer(
    name = "nomad-start-sh-tar-layer",
    directory = "/usr/local/bin",
    files = ["src/test/integration/resources/start.sh"],
)

container_image(
    name = "nomad-start-sh-entrypoint-image",
    base = "@alpine_linux_amd64//image",
    layers = [":nomad-start-sh-tar-layer"],
)

container_run_and_commit(
    name = "nomad-install",
    commands = [
        "addgroup nomad",
        "adduser -S -G nomad nomad",
        "apk --update add --no-cache --virtual .gosu-deps dpkg curl gnupg",
        "apk add --no-cache ca-certificates openssl",
        "update-ca-certificates",
        "curl -L -o /tmp/glibc-2.31-r0.apk https://github.com/andyshinn/alpine-pkg-glibc/releases/download/2.31-r0/glibc-2.31-r0.apk",
        "apk add --allow-untrusted /tmp/glibc-2.31-r0.apk",
        "rm -rf /tmp/glibc-2.31-r0.apk /var/cache/apk/*",
        "curl -L -o /usr/local/bin/dumb-init https://github.com/Yelp/dumb-init/releases/download/v1.2.2/dumb-init_1.2.2_amd64",
        "chmod +x /usr/local/bin/dumb-init",
        "curl -L -o /usr/local/bin/gosu \"https://github.com/tianon/gosu/releases/download/1.11/gosu-amd64\"",
        "chmod +x /usr/local/bin/gosu",
        "gosu nobody true",
        "apk del .gosu-deps",
        "apk --update add --no-cache --virtual .nomad-deps gnupg curl",
        "cd /tmp",
        "curl -L -o nomad_0.11.1_linux_amd64.zip https://releases.hashicorp.com/nomad/0.11.1/nomad_0.11.1_linux_amd64.zip",
        "unzip -d /bin nomad_0.11.1_linux_amd64.zip",
        "chmod +x /bin/nomad",
        "apk del .nomad-deps",
        "mkdir -p /nomad/data",
        "mkdir -p /etc/nomad",
        "chown -R nomad:nomad /nomad",
    ],
    image = ":nomad-start-sh-entrypoint-image.tar",
)

container_image(
    name = "nomad",
    base = ":nomad-install",
    cmd = ["/usr/local/bin/start.sh"],
    entrypoint = ["/usr/local/bin/start.sh"],
    ports = [
        "4646",
        "4647",
        "4648",
    ],
    visibility = ["//visibility:public"],
)

container_push(
    name = "nomad-docker-push-local-dev",
    format = "Docker",
    image = ":nomad.tar",
    registry = "docker.aws.radix.bio",
    repository = "test/timberland-nomad",
    tag = "{STABLE_GIT_BRANCH}",
    tags = [
        "auto-push-docker-image",
    ],
)

scala_library(
    name = "synthetic-integration-lib",
    srcs = ["src/test/integration/src/integration.scala"],
    data = [
        "src/test/integration/resources/docker-compose.yml",
        "src/test/integration/resources/nomad.hcl",
    ],
    visibility = ["//visibility:public"],
    exports = [
        "//3rdparty/jvm/com/dimafeng:testcontainers_scala",
        "//3rdparty/jvm/io/7mind/izumi:logstage_core",
        "//3rdparty/jvm/org/scalatest",
        "//timberland/jvm:timberland-lib",
    ],
    deps = [
        "//3rdparty/jvm/com/dimafeng:testcontainers_scala",
        "//3rdparty/jvm/io/7mind/izumi:logstage_core",
        "//3rdparty/jvm/org/scalatest",
        "//timberland/jvm:timberland-lib",
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
