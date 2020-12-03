workspace(name = "monorepo")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file")
load("@bazel_tools//tools/build_defs/repo:jvm.bzl", "jvm_maven_import_external")
load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository", "new_git_repository")

skylib_version = "1.0.3"

http_archive(
    name = "bazel_skylib",
    sha256 = "1c531376ac7e5a180e0237938a2536de0c54d93f5c278634818e0efc952dd56c",
    type = "tar.gz",
    url = "https://github.com/bazelbuild/bazel-skylib/releases/download/{}/bazel-skylib-{}.tar.gz".format(skylib_version, skylib_version),
)

rules_scala_version = "eabb1d28fb288fb5b15857260f87818dda5a97c8"  # update this as needed

http_archive(
    name = "z3-darwin",
    build_file_content = """
package(default_visibility = ["//visibility:public"])
java_import(
    name = "z3-jar",
    jars = ["z3-4.8.7-x64-osx-10.14.6/bin/com.microsoft.z3.jar"]
)
cc_import(
    name = "z3_import_versioned",
    shared_library = "z3-4.8.7-x64-osx-10.14.6/bin/libz3.dylib",
)
filegroup(
    name = "libz3.so.4.8",
    srcs = [
        "z3-4.8.7-x64-osx-10.14.6/bin/libz3.dylib"
    ]
)
filegroup(
    name = "libz3java.so",
    srcs = [
        "z3-4.8.7-x64-osx-10.14.6/bin/libz3java.dylib"
    ]
)
cc_import(
    name = "z3java",
    shared_library =  "z3-4.8.7-x64-osx-10.14.6/bin/libz3java.dylib"
)
    """,
    patch_cmds = [
        "install_name_tool -change libz3.dylib @loader_path/libz3.dylib z3-4.8.7-x64-osx-10.14.6/bin/libz3java.dylib",
    ],
    sha256 = "49fa41210ff572ae56476befafbeb4a82bbf921f843daf73ef5451f7bcd6d2c5",
    url = "https://github.com/Z3Prover/z3/releases/download/z3-4.8.7/z3-4.8.7-x64-osx-10.14.6.zip",
)

http_archive(
    name = "z3-linux",
    build_file_content = """
package(default_visibility = ["//visibility:public"])
java_import(
    name = "z3-jar",
    jars = ["z3-4.8.7-x64-ubuntu-16.04/bin/com.microsoft.z3.jar"]
)
cc_import(
    name = "z3_import_versioned",
    shared_library = "z3-4.8.7-x64-ubuntu-16.04/bin/libz3.so",
)
filegroup(
    name = "libz3.so.4.8",
    srcs = [
        "z3-4.8.7-x64-ubuntu-16.04/bin/libz3.so"
    ]
)
filegroup(
    name = "libz3java.so",
    srcs = [
        "z3-4.8.7-x64-ubuntu-16.04/bin/libz3java.so"
    ]
)
cc_import(
    name = "z3java",
    shared_library =  "z3-4.8.7-x64-ubuntu-16.04/bin/libz3java.so"
)
    """,
    patch_cmds = ["patchelf --set-rpath '$ORIGIN' z3-4.8.7-x64-ubuntu-16.04/bin/libz3java.so"],
    sha256 = "fcde3273ba88e291fe93db4b9d39957274700caeebba8aefbae28796da0dc0b7",
    url = "https://github.com/Z3Prover/z3/releases/download/z3-4.8.7/z3-4.8.7-x64-ubuntu-16.04.zip",
)

http_archive(
    name = "or_tools-darwin",
    build_file_content = """
package(default_visibility = ["//visibility:public"])

java_import(
    name = "ortools-java-jar",
    jars = [
        "or-tools_MacOsX-10.14.4_v7.0.6546/lib/com.google.ortools.jar",
        "or-tools_MacOsX-10.14.4_v7.0.6546/lib/protobuf.jar",
    ]
)
java_library(
    name = "ortools-java",
    resources = [
        "or-tools_MacOsX-10.14.4_v7.0.6546/lib/libprotobuf.3.6.1.dylib",
        "or-tools_MacOsX-10.14.4_v7.0.6546/lib/libortools.dylib",
        "or-tools_MacOsX-10.14.4_v7.0.6546/lib/libjniortools.dylib",
        "or-tools_MacOsX-10.14.4_v7.0.6546/lib/libglog.0.3.5.dylib",
        "or-tools_MacOsX-10.14.4_v7.0.6546/lib/libgflags.2.2.dylib",
        "or-tools_MacOsX-10.14.4_v7.0.6546/lib/libCbcSolver.3.dylib",
        "or-tools_MacOsX-10.14.4_v7.0.6546/lib/libCbc.3.dylib",
        "or-tools_MacOsX-10.14.4_v7.0.6546/lib/libClp.1.dylib",
        "or-tools_MacOsX-10.14.4_v7.0.6546/lib/libClpSolver.1.dylib",
        "or-tools_MacOsX-10.14.4_v7.0.6546/lib/libOsiClp.1.dylib",
        "or-tools_MacOsX-10.14.4_v7.0.6546/lib/libOsiCbc.3.dylib",
        "or-tools_MacOsX-10.14.4_v7.0.6546/lib/libCoinUtils.3.dylib",
        "or-tools_MacOsX-10.14.4_v7.0.6546/lib/libCgl.1.dylib",
        "or-tools_MacOsX-10.14.4_v7.0.6546/lib/libOsi.1.dylib"
    ],
    resource_strip_prefix = "or-tools_MacOsX-10.14.4_v7.0.6546/lib",
    exports = [
        ":ortools-java-jar"
    ],
    runtime_deps = [
        ":ortools-java-jar"
    ],
    visibility = ["//visibility:public"]
)
    """,
    patch_cmds = ["mv or-tools_MacOsX-10.14.4_v7.0.6546/lib/libjniortools.jnilib or-tools_MacOsX-10.14.4_v7.0.6546/lib/libjniortools.dylib"],
    sha256 = "b924ea29619598282a31b74e8012c58f391985fb26f025b147c13fa7985d88fd",
    url = "https://github.com/google/or-tools/releases/download/v7.0/or-tools_MacOsX-10.14.4_v7.0.6546.tar.gz",
)

http_archive(
    name = "or_tools-linux",
    build_file_content = """
package(default_visibility = ["//visibility:public"])

java_import(
    name = "ortools-java-jar",
    jars = [
        "or-tools_Debian-9.8-64bit_v7.0.6546/lib/com.google.ortools.jar",
        "or-tools_Debian-9.8-64bit_v7.0.6546/lib/protobuf.jar",
    ]
)
java_library(
    name = "ortools-java",
    resources = [
        "or-tools_Debian-9.8-64bit_v7.0.6546/lib/libortools.so",
        "or-tools_Debian-9.8-64bit_v7.0.6546/lib/libjniortools.so",
        "or-tools_Debian-9.8-64bit_v7.0.6546/lib/libglog.so.0.3.5",
        "or-tools_Debian-9.8-64bit_v7.0.6546/lib/libprotobuf.so.3.6.1",
        "or-tools_Debian-9.8-64bit_v7.0.6546/lib/libgflags.so.2.2",
        "or-tools_Debian-9.8-64bit_v7.0.6546/lib/libCbcSolver.so.3",
        "or-tools_Debian-9.8-64bit_v7.0.6546/lib/libCbc.so.3",
        "or-tools_Debian-9.8-64bit_v7.0.6546/lib/libClp.so.1",
        "or-tools_Debian-9.8-64bit_v7.0.6546/lib/libClpSolver.so.1",
        "or-tools_Debian-9.8-64bit_v7.0.6546/lib/libOsiClp.so.1",
        "or-tools_Debian-9.8-64bit_v7.0.6546/lib/libOsiCbc.so.3",
        "or-tools_Debian-9.8-64bit_v7.0.6546/lib/libCoinUtils.so.3",
        "or-tools_Debian-9.8-64bit_v7.0.6546/lib/libCgl.so.1",
        "or-tools_Debian-9.8-64bit_v7.0.6546/lib/libOsi.so.1"
    ],
    resource_strip_prefix = "or-tools_Debian-9.8-64bit_v7.0.6546/lib/",
    exports = [
        ":ortools-java-jar"
    ],
    runtime_deps = [
        ":ortools-java-jar"
    ],
    visibility = ["//visibility:public"]
)
    """,
    sha256 = "98157fafddacd33d8360cdfd36d1bcb8d8fe056d8d70f5947caf1e4ddc4257d5",
    url = "https://github.com/google/or-tools/releases/download/v7.0/or-tools_debian-9_v7.0.6546.tar.gz",
)

http_archive(
    name = "io_bazel_rules_scala",
    sha256 = "c75f3f6725369171f7a670767a28fd488190070fc9f31d882d9b7a61caffeb26",
    strip_prefix = "rules_scala-%s" % rules_scala_version,
    type = "zip",
    url = "https://github.com/bazelbuild/rules_scala/archive/%s.zip" % rules_scala_version,
)

load(
    "@io_bazel_rules_scala//scala/scalafmt:scalafmt_repositories.bzl",
    "scalafmt_default_config",
    "scalafmt_repositories",
)

scalafmt_repositories()

scalafmt_default_config()

bind(
    name = "io_bazel_rules_scala_dependency_scalap_scalap",
    actual = "//3rdparty/jvm/org/scala-lang:scalap",
)

bind(
    name = "io_bazel_rules_scala_dependency_scalatest_scalatest",
    actual = "//3rdparty/jvm/org/scalatest:scalatest",
)

bind(
    name = "io_bazel_rules_scala_dependency_scala_scalactic_scalactic",
    actual = "//3rdparty/jvm/org/scalactic:scalactic",
)

git_repository(
    name = "rules_pkg",
    commit = "7636b7dc2e14bf198a6c21c01e33847f3863e572",
    patch_cmds = ["mv pkg/* ."],
    remote = "https://github.com/itdaniher/rules_pkg.git",
)

load("@rules_pkg//:deps.bzl", "rules_pkg_dependencies")
load(
    "@io_bazel_rules_scala//scala:toolchains.bzl",
    "scala_register_toolchains",
)

scala_register_toolchains()

load(
    "@io_bazel_rules_scala//scala:scala.bzl",
    "scala_repositories",
)

load(
    "@io_bazel_rules_scala//jmh:jmh.bzl",
    "jmh_repositories"
)
jmh_repositories()


scala_repositories(
    scala_extra_jars = {
        "2.12": {
            "scalatest": {
                "version": "3.0.8",
                "sha256": "a4045cea66f3eaab525696f3000d7d610593778bd070e98349a7066f872844cd",
            },
            "scalactic": {
                "version": "3.0.8",
                "sha256": "5f9ad122f54e9a0112ff4fcaadfb2802d8796f5dde021caa4c831067fca68469",
            },
            "scala_xml": {
                "version": "1.0.5",
                "sha256": "035015366f54f403d076d95f4529ce9eeaf544064dbc17c2d10e4f5908ef4256",
            },
            "scala_parser_combinators": {
                "version": "1.0.4",
                "sha256": "282c78d064d3e8f09b3663190d9494b85e0bb7d96b0da05994fe994384d96111",
            },
        },
    },
    scala_version_shas = (
        "2.12.8",
        {
            "scala_compiler": "f34e9119f45abd41e85b9e121ba19dd9288b3b4af7f7047e86dc70236708d170",
            "scala_library": "321fb55685635c931eba4bc0d7668349da3f2c09aee2de93a70566066ff25c28",
            "scala_reflect": "4d6405395c4599ce04cea08ba082339e3e42135de9aae2923c9f5367e957315a",
            "scalajs_compiler": "fc54c1a5f08598c3aef8b4dd13cf482323b56cb416547da9944655d7c53eae32",
        },
    ),
)

#register_toolchains("//:scala_toolchain")

protobuf_version = "3.11.3"

protobuf_version_sha256 = "cf754718b0aa945b00550ed7962ddc167167bd922b842199eeb6505e6f344852"

http_archive(
    name = "com_google_protobuf",
    sha256 = protobuf_version_sha256,
    strip_prefix = "protobuf-%s" % protobuf_version,
    url = "https://github.com/protocolbuffers/protobuf/archive/v%s.tar.gz" % protobuf_version,
)

load(
    "@io_bazel_rules_scala//scala:scala_cross_version.bzl",
    "default_scala_major_version",
    "scala_mvn_artifact",
)
load(
    "@io_bazel_rules_scala//scala:scala_maven_import_external.bzl",
    "scala_maven_import_external",
)
load("//3rdparty:workspace.bzl", "maven_dependencies")

maven_dependencies()

load("//3rdparty:target_file.bzl", "build_external_workspace")

build_external_workspace(name = "third_party")

http_archive(
    name = "io_bazel_rules_docker",
    sha256 = "dc97fccceacd4c6be14e800b2a00693d5e8d07f69ee187babfd04a80a9f8e250",
    strip_prefix = "rules_docker-0.14.1",
    urls = ["https://github.com/bazelbuild/rules_docker/releases/download/v0.14.1/rules_docker-v0.14.1.tar.gz"],
)

load(
    "@io_bazel_rules_docker//repositories:repositories.bzl",
    container_repositories = "repositories",
)

container_repositories()

load("@io_bazel_rules_docker//container:container.bzl", "container_pull")

container_pull(
    name = "openjdk-base",
    digest = "sha256:4f70a5fa4d957a6de322ad2f548eea79f04d1bc71f2a842f79897bd34ec38b3e",
    registry = "index.docker.io",
    repository = "adoptopenjdk/openjdk11",
)

container_pull(
    name = "golang",
    registry = "index.docker.io",
    repository = "golang",
    tag = "latest",
)

load(
    "@io_bazel_rules_docker//scala:image.bzl",
    _scala_image_repos = "repositories",
)

rules_pkg_dependencies()

_scala_image_repos()

scala_maven_import_external(
    name = "org_scala_graph_graph_core_sjs0_6_2_12",
    artifact = "org.scala-graph:graph-core_sjs0.6_2.12:1.12.5",
    licenses = [],
    server_urls = [
        "https://repo1.maven.org/maven2/",
        "https://oss.sonatype.org/content/repositories/releases",
        "https://oss.sonatype.org/content/repositories/snapshots",
        "https://packages.confluent.io/maven/",
        "https://oss.sonatype.org/content/repositories/releases",
        "https://oss.sonatype.org/content/repositories/snapshots",
        "https://packages.confluent.io/maven/",
    ],
)

bind(
    name = "jar/org/scala/graph/graph_core_sjs0_6_2_12",
    actual = "@org_scala_graph_graph_core_sjs0_6_2_12//jar",
)

scala_maven_import_external(
    name = "org_scalaz_scalaz_core_sjs0_6_2_12",
    artifact = "org.scalaz:scalaz-core_sjs0.6_2.12:7.2.15",
    licenses = [],
    server_urls = [
        "https://repo1.maven.org/maven2/",
        "https://oss.sonatype.org/content/repositories/releases",
        "https://oss.sonatype.org/content/repositories/snapshots",
        "https://packages.confluent.io/maven/",
        "https://oss.sonatype.org/content/repositories/releases",
        "https://oss.sonatype.org/content/repositories/snapshots",
        "https://packages.confluent.io/maven/",
    ],
)

bind(
    name = "jar/org/scalaz/scalaz_core_sjs0_6_2_12",
    actual = "@org_scalaz_scalaz_core_sjs0_6_2_12//jar",
)

scala_maven_import_external(
    name = "org_typelevel_cats_core_sjs0_6_2_12",
    artifact = "org.typelevel:cats-core_sjs0.6_2.12:1.1.0",
    licenses = [],
    server_urls = [
        "https://repo1.maven.org/maven2/",
        "https://oss.sonatype.org/content/repositories/releases",
        "https://oss.sonatype.org/content/repositories/snapshots",
        "https://packages.confluent.io/maven/",
        "https://oss.sonatype.org/content/repositories/releases",
        "https://oss.sonatype.org/content/repositories/snapshots",
        "https://packages.confluent.io/maven/",
    ],
)

bind(
    name = "jar/org/typelevel/cats_core_sjs0_6_2_12",
    actual = "@org_typelevel_cats_core_sjs0_6_2_12//jar",
)

scala_maven_import_external(
    name = "org_typelevel_cats_kernel_sjs0_6_2_12",
    artifact = "org.typelevel:cats-kernel_sjs0.6_2.12:1.1.0",
    licenses = [],
    server_urls = [
        "https://repo1.maven.org/maven2/",
        "https://oss.sonatype.org/content/repositories/releases",
        "https://oss.sonatype.org/content/repositories/snapshots",
        "https://packages.confluent.io/maven/",
        "https://oss.sonatype.org/content/repositories/releases",
        "https://oss.sonatype.org/content/repositories/snapshots",
        "https://packages.confluent.io/maven/",
    ],
)

bind(
    name = "jar/org/typelevel/cats_kernel_sjs0_6_2_12",
    actual = "@org_typelevel_cats_kernel_sjs0_6_2_12//jar",
)

scala_maven_import_external(
    name = "org_typelevel_cats_macros_sjs0_6_2_12",
    artifact = "org.typelevel:cats-macros_sjs0.6_2.12:1.1.0",
    licenses = [],
    server_urls = [
        "https://repo1.maven.org/maven2/",
        "https://oss.sonatype.org/content/repositories/releases",
        "https://oss.sonatype.org/content/repositories/snapshots",
        "https://packages.confluent.io/maven/",
        "https://oss.sonatype.org/content/repositories/releases",
        "https://oss.sonatype.org/content/repositories/snapshots",
        "https://packages.confluent.io/maven/",
    ],
)

bind(
    name = "jar/org/typelevel/cats_macros_sjs0_6_2_12",
    actual = "@org_typelevel_cats_macros_sjs0_6_2_12//jar",
)

scala_maven_import_external(
    name = "org_typelevel_machinist_sjs0_6_2_12",
    artifact = "org.typelevel:machinist_sjs0.6_2.12:0.6.2",
    licenses = [],
    server_urls = [
        "https://repo1.maven.org/maven2/",
        "https://oss.sonatype.org/content/repositories/releases",
        "https://oss.sonatype.org/content/repositories/snapshots",
        "https://packages.confluent.io/maven/",
        "https://oss.sonatype.org/content/repositories/releases",
        "https://oss.sonatype.org/content/repositories/snapshots",
        "https://packages.confluent.io/maven/",
    ],
)

bind(
    name = "jar/org/typelevel/machinist_sjs0_6_2_12",
    actual = "@org_typelevel_machinist_sjs0_6_2_12//jar",
)

scala_maven_import_external(
    name = "org_typelevel_squants_sjs0_6_2_12",
    artifact = "org.typelevel:squants_sjs0.6_2.12:1.3.0",
    licenses = [],
    server_urls = [
        "https://repo1.maven.org/maven2/",
        "https://oss.sonatype.org/content/repositories/releases",
        "https://oss.sonatype.org/content/repositories/snapshots",
        "https://packages.confluent.io/maven/",
        "https://oss.sonatype.org/content/repositories/releases",
        "https://oss.sonatype.org/content/repositories/snapshots",
        "https://packages.confluent.io/maven/",
    ],
)

bind(
    name = "jar/org/typelevel/squants_sjs0_6_2_12",
    actual = "@org_typelevel_squants_sjs0_6_2_12//jar",
)

jvm_maven_import_external(
    name = "scalajs_ir",
    artifact = "org.scala-js:scalajs-ir_2.12:0.6.28",
    server_urls = ["http://central.maven.org/maven2"],
)

jvm_maven_import_external(
    name = "scalajs_tools",
    artifact = "org.scala-js:scalajs-tools_2.12:0.6.28",
    server_urls = ["http://central.maven.org/maven2"],
)

git_repository(
    name = "scalaz3",
    commit = "63c7674320e585ee686a1de805d3d868b2019106",
    remote = "git@gitlab.com:radix-labs/scalaz3.git",
    shallow_since = "1593227973 +0000",
)

http_archive(
    name = "sbt",
    build_file_content = """filegroup(name = "all", srcs = glob(["**"]), visibility = ["//visibility:public"])""",
    sha256 = "4ea0b6fe056fd521eb6f785ee2e4694a2a0128b5de362e233cab0c1a20eb04eb",
    url = "https://github.com/sbt/sbt/releases/download/v1.4.2/sbt-1.4.2.zip"
)

http_archive(
    name = "consul",
    build_file_content = "exports_files([\"consul\"])",
    sha256 = "409b964f9cec93ba4aa3f767fe3a57e14160d86ffab63c3697d188ba29d247ce",
    url = "https://releases.hashicorp.com/consul/1.9.0/consul_1.9.0_linux_amd64.zip",
)

http_archive(
    name = "consul-template",
    build_file_content = "exports_files([\"consul-template\"])",
    sha256 = "58356ec125c85b9705dc7734ed4be8491bb4152d8a816fd0807aed5fbb128a7b",
    url = "https://releases.hashicorp.com/consul-template/0.25.1/consul-template_0.25.1_linux_amd64.zip",
)

http_archive(
    name = "vault",
    build_file_content = "exports_files([\"vault\"])",
    sha256 = "83048e2d1ebfea212fead42e474e947c3a3bccc5056a5158ed33f530f8325e39",
    url = "https://releases.hashicorp.com/vault/1.6.0/vault_1.6.0_linux_amd64.zip",
)

http_archive(
    name = "vault-plugin-secrets-oauthapp",
    build_file_content = "exports_files([\"vault-plugin-secrets-oauthapp\"])",
    patch_cmds = ["mv vault-plugin-secrets-oauthapp-v1.3.0-linux-amd64 vault-plugin-secrets-oauthapp"],
    sha256 = "5ed0f0df011ede9426fbe59c11ac9d16d0d769c5ed14878ddcf8b931c87fc119",
    url = "https://github.com/puppetlabs/vault-plugin-secrets-oauthapp/releases/download/v1.3.0/vault-plugin-secrets-oauthapp-v1.3.0-linux-amd64.tar.xz",
)

http_archive(
    name = "nomad",
    build_file_content = "exports_files([\"nomad\"])",
    sha256 = "4d14053b1fc3cfcfca81e8914f9f5b559025fbc07ee94c5a87bdc114826d7145",
    url = "https://releases.hashicorp.com/nomad/0.12.9/nomad_0.12.9_linux_amd64.zip",
)

http_archive(
    name = "terraform",
    build_file_content = "exports_files([\"terraform\"])",
    sha256 = "322fe01fb54118ba25c66f4a406d25e3d8625de062d78544579e9385a3974496",
    url = "https://releases.hashicorp.com/terraform/0.14.0-rc1/terraform_0.14.0-rc1_linux_amd64.zip",
)

http_file(
    name = "terraform-provider-nomad",
    downloaded_file_path = "nomad/terraform-provider-nomad_1.4.8_linux_amd64.zip",
    sha256 = "122a87b8c09b12ab29641f198db2db13d8f559346996d6472ad5bb676de1002b",
    urls = ["https://releases.hashicorp.com/terraform-provider-nomad/1.4.8/terraform-provider-nomad_1.4.8_linux_amd64.zip"],
)

http_file(
    name = "terraform-provider-consul",
    downloaded_file_path = "consul/terraform-provider-consul_2.7.0_linux_amd64.zip",
    sha256 = "6cc007f02065258d2e7e9d04d196aa7c16731f11f5923e06d78488e14be2c8a5",
    urls = ["https://releases.hashicorp.com/terraform-provider-consul/2.7.0/terraform-provider-consul_2.7.0_linux_amd64.zip"],
)

http_file(
    name = "terraform-provider-vault",
    downloaded_file_path = "vault/terraform-provider-vault_2.11.0_linux_amd64.zip",
    sha256 = "01ecd700ad6887de8c0c88df265f5f2fa2601116348aef11a070d05250882a0c",
    urls = ["https://releases.hashicorp.com/terraform-provider-vault/2.11.0/terraform-provider-vault_2.11.0_linux_amd64.zip"],
)

http_archive(
    name = "containernetworking-cni-plugin",
    build_file_content = """
exports_files([
  "bandwidth",
  "firewall",
  "host-device",
  "ipvlan",
  "macvlan",
  "ptp",
  "static",
  "vlan",
  "bridge",
  "dhcp",
  "flannel",
  "host-local",
  "loopback",
  "portmap",
  "sbr",
  "tuning",
])""",
    sha256 = "bd682ffcf701e8f83283cdff7281aad0c83b02a56084d6e601216210732833f9",
    url = "https://github.com/containernetworking/plugins/releases/download/v0.8.5/cni-plugins-linux-amd64-v0.8.5.tgz",
)

http_archive(
    name = "containernetworking-cni-plugin",
    build_file_content = """
exports_files([
  "bandwidth",
  "firewall",
  "host-device",
  "ipvlan",
  "macvlan",
  "ptp",
  "static",
  "vlan",
  "bridge",
  "dhcp",
  "flannel",
  "host-local",
  "loopback",
  "portmap",
  "sbr",
  "tuning",
])""",
    sha256 = "bd682ffcf701e8f83283cdff7281aad0c83b02a56084d6e601216210732833f9",
    url = "https://github.com/containernetworking/plugins/releases/download/v0.8.5/cni-plugins-linux-amd64-v0.8.5.tgz",
)

http_archive(
    name = "jaxws",
    build_file_content = "exports_files([\"metro/lib/webservices-tools.jar\"])",
    url = "https://maven.java.net/content/repositories/releases//org/glassfish/metro/metro-standalone/2.3.1/metro-standalone-2.3.1.zip",
)

### AArch64 binaries

http_archive(
    name = "consul_arm",
    build_file_content = "exports_files([\"consul\"])",
    sha256 = "71b3c5551fbd297efbee74f95b138244202fcdd745b80033b99e1bc3d06043ee",
    url = "https://releases.hashicorp.com/consul/1.9.0/consul_1.9.0_linux_arm64.zip",
)

http_archive(
    name = "consul-template_arm",
    build_file_content = "exports_files([\"consul-template\"])",
    sha256 = "d27041303b64695f29fd8ce7d26768eeaaaa51c6187c6dc37ee4d79c449501ba",
    url = "https://releases.hashicorp.com/consul-template/0.25.1/consul-template_0.25.1_linux_arm64.zip",
)

http_archive(
    name = "vault_arm",
    build_file_content = "exports_files([\"vault\"])",
    sha256 = "fff13c7f753ebee9ebea88e81f5d8dfa3d62f15ea0efae4287f09a4e422c0a05",
    url = "https://releases.hashicorp.com/vault/1.6.0/vault_1.6.0_linux_arm64.zip",
)

http_archive(
    name = "vault-plugin-secrets-oauthapp_arm",
    build_file_content = "exports_files([\"vault-plugin-secrets-oauthapp\"])",
    patch_cmds = ["mv vault-plugin-secrets-oauthapp-v1.3.0-linux-arm64 vault-plugin-secrets-oauthapp"],
    sha256 = "d3d2bb70972d5279a11b6f873d47a65f6221f1ff4637e88644f627ce9c05dd8f",
    url = "https://github.com/puppetlabs/vault-plugin-secrets-oauthapp/releases/download/v1.3.0/vault-plugin-secrets-oauthapp-v1.3.0-linux-arm64.tar.xz",
)

http_archive(
    name = "nomad_arm",
    build_file_content = "exports_files([\"nomad\"])",
    sha256 = "13311848b9db9931662173c9ddb03623403b99c1bb991a2032927eecb7d8b15d",
    url = "https://releases.hashicorp.com/nomad/0.12.9/nomad_0.12.9_linux_arm64.zip",
)

http_archive(
    name = "terraform_arm",
    build_file_content = "exports_files([\"terraform\"])",
    sha256 = "7475073aec8ccf837197a2d8a12130a3538b05f29825d942c6eaaf674c078565",
    url = "https://releases.hashicorp.com/terraform/0.14.0-rc1/terraform_0.14.0-rc1_linux_arm.zip",
)

http_file(
    name = "terraform-provider-nomad_arm",
    downloaded_file_path = "nomad/terraform-provider-nomad_1.4.8_linux_arm.zip",
    sha256 = "3c6c009eaf83c3be9298e3799fea68b4c21fc7fb6077849f8e91865ece03cc93",
    urls = ["https://releases.hashicorp.com/terraform-provider-nomad/1.4.8/terraform-provider-nomad_1.4.8_linux_arm.zip"],
)

http_file(
    name = "terraform-provider-consul_arm",
    downloaded_file_path = "consul/terraform-provider-consul_2.7.0_linux_arm.zip",
    sha256 = "23d356b293e209c3cefe42f8408468a3f808efa8f826096a0a7423f8b7d500bc",
    urls = ["https://releases.hashicorp.com/terraform-provider-consul/2.7.0/terraform-provider-consul_2.7.0_linux_arm.zip"],
)

http_file(
    name = "terraform-provider-vault_arm",
    downloaded_file_path = "vault/terraform-provider-vault_2.11.0_linux_arm.zip",
    sha256 = "36bb7ccf11cd4aa8f37626116a4c10820f95ca78773b5c74d8c8e51f50a75a0d",
    urls = ["https://releases.hashicorp.com/terraform-provider-vault/2.11.0/terraform-provider-vault_2.11.0_linux_arm.zip"],
)

### Windows binaries

http_archive(
    name = "consul_win",
    build_file_content = "exports_files([\"consul.exe\"])",
    sha256 = "1cd7736b799a8c2ab1efd57020037a40f51061bac3bee07a518c8a4c87eb965d",
    url = "https://releases.hashicorp.com/consul/1.9.0/consul_1.9.0_windows_amd64.zip",
)

http_archive(
    name = "consul-template_win",
    build_file_content = "exports_files([\"consul-template.exe\"])",
    sha256 = "be521a2588ba7f1be1493a65b82f24ee032e562dfc0bf52081c039cc359afa54",
    url = "https://releases.hashicorp.com/consul-template/0.25.1/consul-template_0.25.1_windows_amd64.zip",
)

http_archive(
    name = "vault_win",
    build_file_content = "exports_files([\"vault.exe\"])",
    sha256 = "f4df525126885663a622478de34a9e6970985e29875b86cbb5a72860b7ab1a3f",
    url = "https://releases.hashicorp.com/vault/1.6.0/vault_1.6.0_windows_amd64.zip",
)

http_archive(
    name = "vault-plugin-secrets-oauthapp_win",
    build_file_content = "exports_files([\"vault-plugin-secrets-oauthapp.exe\"])",
    patch_cmds = ["mv vault-plugin-secrets-oauthapp-v1.3.0-windows-amd64.exe vault-plugin-secrets-oauthapp.exe"],
    sha256 = "2301d04913dc861f7e0375ae63782ecdf63438c25c4c1616e7e906121c557780",
    url = "https://github.com/puppetlabs/vault-plugin-secrets-oauthapp/releases/download/v1.3.0/vault-plugin-secrets-oauthapp-v1.3.0-windows-amd64.zip",
)

http_archive(
    name = "nomad_win",
    build_file_content = "exports_files([\"nomad.exe\"])",
    sha256 = "64f4c9594278f688b22613b05940c0510438ba38ede44e0fde9741a4963ad6e1",
    url = "https://releases.hashicorp.com/nomad/0.12.9/nomad_0.12.9_windows_amd64.zip",
)

http_archive(
    name = "terraform_win",
    build_file_content = "exports_files([\"terraform.exe\"])",
    sha256 = "d5bea3f5aa34c83f79153e84916e6014bb4ef1455f2cc1808f0e539c2a163561",
    url = "https://releases.hashicorp.com/terraform/0.14.0-rc1/terraform_0.14.0-rc1_windows_amd64.zip",
)

http_file(
    name = "terraform-provider-nomad_win",
    downloaded_file_path = "nomad/terraform-provider-nomad_1.4.8_linux_win.zip",
    sha256 = "8e5c6515c5a3f5ee46466e8f5159587b31cbb4eb12d313267e87e04e519fe60d",
    urls = ["https://releases.hashicorp.com/terraform-provider-nomad/1.4.8/terraform-provider-nomad_1.4.8_windows_amd64.zip"],
)

http_file(
    name = "terraform-provider-consul_win",
    downloaded_file_path = "consul/terraform-provider-consul_2.7.0_linux_win.zip",
    sha256 = "f9cf8674af1d0687f317a29219523f3a7d4223272fe969670ea2d4f60b3cc16f",
    urls = ["https://releases.hashicorp.com/terraform-provider-consul/2.7.0/terraform-provider-consul_2.7.0_windows_amd64.zip"],
)

http_file(
    name = "terraform-provider-vault_win",
    downloaded_file_path = "vault/terraform-provider-vault_2.11.0_linux_win.zip",
    sha256 = "46d875dcbfcba1442cb286ca1522d10f456b80de0a9456db19025acc5baf9e68",
    urls = ["https://releases.hashicorp.com/terraform-provider-vault/2.11.0/terraform-provider-vault_2.11.0_windows_amd64.zip"],
)


http_archive(
  name = "rules_jmh",
  strip_prefix = "buchgr-rules_jmh-a5f0231",
  url = "https://github.com/buchgr/rules_jmh/zipball/a5f0231ebfde44b4904c7d101b9f269b96c86d06",
  type = "zip",
#  sha256 = "dbb7d7e5ec6e932eddd41b910691231ffd7b428dff1ef9a24e4a9a59c1a1762d",
)

load("@rules_jmh//:deps.bzl", "rules_jmh_deps")
rules_jmh_deps()
load("@rules_jmh//:defs.bzl", "rules_jmh_maven_deps")
rules_jmh_maven_deps()