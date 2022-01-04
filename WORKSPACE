workspace(
    name = "monorepo",
    managed_directories = {"@npm": ["interface/node_modules"]},
)

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file")
load("@bazel_tools//tools/build_defs/repo:jvm.bzl", "jvm_maven_import_external")
load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository", "new_git_repository")

http_archive(
    name = "io_bazel_rules_go",
    sha256 = "2b1641428dff9018f9e85c0384f03ec6c10660d935b750e3fa1492a281a53b0f",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_go/releases/download/v0.29.0/rules_go-v0.29.0.zip",
        "https://github.com/bazelbuild/rules_go/releases/download/v0.29.0/rules_go-v0.29.0.zip",
    ],
)

load("@io_bazel_rules_go//go:deps.bzl", "go_register_toolchains", "go_rules_dependencies")

go_rules_dependencies()

go_register_toolchains(go_version = "1.17.1")

http_archive(
    name = "bazel_gazelle",
    sha256 = "de69a09dc70417580aabf20a28619bb3ef60d038470c7cf8442fafcf627c21cb",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/bazel-gazelle/releases/download/v0.24.0/bazel-gazelle-v0.24.0.tar.gz",
        "https://github.com/bazelbuild/bazel-gazelle/releases/download/v0.24.0/bazel-gazelle-v0.24.0.tar.gz",
    ],
)

load("@bazel_gazelle//:deps.bzl", "gazelle_dependencies")

gazelle_dependencies()

skylib_version = "1.0.3"

http_archive(
    name = "bazel_skylib",
    sha256 = "1c531376ac7e5a180e0237938a2536de0c54d93f5c278634818e0efc952dd56c",
    type = "tar.gz",
    url = "https://github.com/bazelbuild/bazel-skylib/releases/download/{}/bazel-skylib-{}.tar.gz".format(skylib_version, skylib_version),
)

rules_scala_version = "eabb1d28fb288fb5b15857260f87818dda5a97c8"  # update this as needed

http_archive(
    name = "rules_rust",
    sha256 = "accb5a89cbe63d55dcdae85938e56ff3aa56f21eb847ed826a28a83db8500ae6",
    strip_prefix = "rules_rust-9aa49569b2b0dacecc51c05cee52708b7255bd98",
    urls = [
        # Main branch as of 2021-02-19
        "https://github.com/bazelbuild/rules_rust/archive/9aa49569b2b0dacecc51c05cee52708b7255bd98.tar.gz",
    ],
)

load("@rules_rust//rust:repositories.bzl", "rust_repositories")

rust_repositories()

http_archive(
    name = "cargo_raze",
    patch_args = [
        "-p1",
    ],
    patches = ["//tools:0001-patch-pcre-to-use-additional-URL.patch"],
    sha256 = "e04a1982ce4f81ffe42066256cfcfc03732e4f1d646fd3253bcf3eabf45f45be",
    strip_prefix = "cargo-raze-0.13.0",
    url = "https://github.com/google/cargo-raze/archive/refs/tags/v0.13.0.tar.gz",
)

load("@cargo_raze//:repositories.bzl", "cargo_raze_repositories")

cargo_raze_repositories()

load("@cargo_raze//:transitive_deps.bzl", "cargo_raze_transitive_deps")

cargo_raze_transitive_deps()

load("//3rdparty:crates.bzl", "raze_fetch_remote_crates")

raze_fetch_remote_crates()

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

http_file(
    name = "tclkit",
    downloaded_file_path = "tclkit",
    executable = True,
    sha256 = "15754d574bfbb389193574692ab81216869115cc953d688d5214088c46f1d02d",
    urls = ["http://kitcreator.rkeene.org/kits/9b4cd5e5fc4b060215ceded44a3e08e2312d5137/tclkit"],
)

http_file(
    name = "bitrock-unpacker",
    downloaded_file_path = "bitrock-unpacker.tcl",
    executable = True,
    sha256 = "d76645a77a04f8f8968c9780fea393b7eea79eea25d824ab08fb8f2f9913982e",
    urls = ["https://raw.githubusercontent.com/Harakku/bitrock-unpacker/master/bitrock-unpacker.tcl"],
)

http_file(
    name = "ocean-omnidriver-linux",
    downloaded_file_path = "omnidriver-2.56-linux64-installer.bin",
    sha256 = "8a3ac3045a1cb898a3f94f3bc4cf1831036274c8fe678b254cf9ffd409a836a0",
    urls = ["https://www.oceaninsight.com/globalassets/catalog-blocks-and-images/software-downloads-installers/omnidriver-2.56-linux64-installer.bin"],
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
    name = "proguard",
    build_file_content = """
package(default_visibility = ["//visibility:public"])

java_import(
    name = "proguard-jar",
    jars = [
        "proguard-7.0.1/lib/proguard.jar",
    ]
)
java_library(
    name = "proguard-lib",
    runtime_deps = [
        ":proguard-jar"
    ]
)
java_binary(
    name = "proguard",
    main_class = "proguard.ProGuard",
    runtime_deps = [
        ":proguard-lib"
    ]
)

    """,
    sha256 = "b7fd1ee6da650b392ab9fe619f0bfd01f1fe8272620d9471fcfc7908b5216d71",
    url = "https://github.com/Guardsquare/proguard/releases/download/v7.0.1/proguard-7.0.1.tar.gz",
)

rules_scala_version = "5df8033f752be64fbe2cedfd1bdbad56e2033b15"

http_archive(
    name = "io_bazel_rules_scala",
    sha256 = "b7fa29db72408a972e6b6685d1bc17465b3108b620cb56d9b1700cf6f70f624a",
    strip_prefix = "rules_scala-%s" % rules_scala_version,
    type = "zip",
    url = "https://github.com/bazelbuild/rules_scala/archive/%s.zip" % rules_scala_version,
)

# Stores Scala version and other configuration
# 2.12 is a default version, other versions can be use by passing them explicitly:
# scala_config(scala_version = "2.11.12")
load("@io_bazel_rules_scala//:scala_config.bzl", "scala_config")

scala_config(scala_version = "2.13.6")

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_repositories")
load("@io_bazel_rules_scala//scala:toolchains.bzl", "scala_register_toolchains")

# optional: setup ScalaTest toolchain and dependencies
load("@io_bazel_rules_scala//testing:scalatest.bzl", "scalatest_repositories", "scalatest_toolchain")
load(
    "@io_bazel_rules_scala//scala/scalafmt:scalafmt_repositories.bzl",
    "scalafmt_default_config",
    "scalafmt_repositories",
)
load("@io_bazel_rules_scala//testing:scalatest.bzl", "scalatest_repositories", "scalatest_toolchain")

#scala_repositories()
scala_repositories(
    overriden_artifacts = {
        "io_bazel_rules_scala_scalatest": {
            "artifact": "org.scalatest:scalatest_2.13:3.1.4",
            "sha256": "60ec218647411a9262e40bd50433db67d4ab97fd01c56b7e281872951f7bfcc7",
        },
        "io_bazel_rules_scala_scalactic": {
            "artifact": "org.scalactic:scalactic_2.13:3.1.4",
            "sha256": "be6859e48ecaa7ad00bd3520d958909830ad6c30fdd69f9811f19f67d9315e83",
        },
    },
)

scalafmt_default_config()

scalafmt_repositories()

scalatest_repositories()

scalatest_toolchain()

scala_register_toolchains()
#uncomment this for a ton of linter output
#register_toolchains("//tools:my_scala_toolchain")

load(
    "@io_bazel_rules_scala//jmh:jmh.bzl",
    "jmh_repositories",
)

jmh_repositories()

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
    #    "default_scala_major_version",
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

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")

git_repository(
    name = "com_github_johnynek_bazel_jar_jar",
    commit = "171f268569384c57c19474b04aebe574d85fde0d",  # Latest commit SHA as at 2019/02/13
    remote = "https://github.com/johnynek/bazel_jar_jar.git",
)

load(
    "@com_github_johnynek_bazel_jar_jar//:jar_jar.bzl",
    "jar_jar_repositories",
)

jar_jar_repositories()

http_archive(
    name = "io_bazel_rules_docker",
    sha256 = "59536e6ae64359b716ba9c46c39183403b01eabfbd57578e84398b4829ca499a",
    strip_prefix = "rules_docker-0.22.0",
    urls = ["https://github.com/bazelbuild/rules_docker/releases/download/v0.22.0/rules_docker-v0.22.0.tar.gz"],
)

load(
    "@io_bazel_rules_docker//repositories:repositories.bzl",
    container_repositories = "repositories",
)

container_repositories()

load("@io_bazel_rules_docker//container:container.bzl", "container_pull", "container_load")


# this one has the *windows* jdk /include files in /opt/javainclude
container_pull(
    name = "msvc_winjdk",
    registry = "ghcr.io",
    repository = "radixbio/msvc-winjdk",
    tag = "temp",
)

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
    commit = "19016d1a5a2b59b8c4ee91a79049ad4266ab9455",
    remote = "git@gitlab.com:radix-labs/scalaz3.git",
    #    shallow_since = "1626731854 -0400",
)


http_archive(
    name = "consul",
    build_file_content = "exports_files([\"consul\"])",
    sha256 = "abd9a7696e2eeed66fdb28965c220a2ba45ee5cd79ff263557f5392291aab730",
    url = "https://releases.hashicorp.com/consul/1.10.1/consul_1.10.1_linux_amd64.zip",
)

http_archive(
    name = "consul-template",
    build_file_content = "exports_files([\"consul-template\"])",
    sha256 = "0d319977885e0f44562cc5f78e225d8431499cc3a95cd1b3fe560df8556bf64a",
    url = "https://releases.hashicorp.com/consul-template/0.27.0/consul-template_0.27.0_linux_amd64.zip",
)

http_archive(
    name = "vault",
    build_file_content = "exports_files([\"vault\"])",
    sha256 = "bb411f2bbad79c2e4f0640f1d3d5ef50e2bda7d4f40875a56917c95ff783c2db",
    url = "https://releases.hashicorp.com/vault/1.8.1/vault_1.8.1_linux_amd64.zip",
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
    sha256 = "e07ebf9ec81fb04ace94884d2c0b0e0bdee3510d5a203bcae96d8bee9463b418",
    url = "https://releases.hashicorp.com/nomad/1.1.3/nomad_1.1.3_linux_amd64.zip",
)

http_archive(
    name = "terraform",
    build_file_content = "exports_files([\"terraform\"])",
    sha256 = "7ce24478859ab7ca0ba4d8c9c12bb345f52e8efdc42fa3ef9dd30033dbf4b561",
    url = "https://releases.hashicorp.com/terraform/1.0.5/terraform_1.0.5_linux_amd64.zip",
)

http_file(
    name = "terraform-provider-nomad",
    downloaded_file_path = "nomad/terraform-provider-nomad_1.4.15_linux_amd64.zip",
    sha256 = "924120d03bb25c2c8120507b3e95341a42bab62a6cd5866dbd2190c5b336475f",
    urls = ["https://releases.hashicorp.com/terraform-provider-nomad/1.4.15/terraform-provider-nomad_1.4.15_linux_amd64.zip"],
)

http_file(
    name = "terraform-provider-consul",
    downloaded_file_path = "consul/terraform-provider-consul_2.13.0_linux_amd64.zip",
    sha256 = "c8e622923f364f73c2912c8f42025fea2bbd772c7fead87c6260163a67685245",
    urls = ["https://releases.hashicorp.com/terraform-provider-consul/2.13.0/terraform-provider-consul_2.13.0_linux_amd64.zip"],
)

http_file(
    name = "terraform-provider-vault",
    downloaded_file_path = "vault/terraform-provider-vault_2.23.0_linux_amd64.zip",
    sha256 = "7348e43000ac78b216543e31e2531654d47c88d707962650ddd66ad488c657a2",
    urls = ["https://releases.hashicorp.com/terraform-provider-vault/2.23.0/terraform-provider-vault_2.23.0_linux_amd64.zip"],
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
    sha256 = "977824932d5667c7a37aa6a3cbba40100a6873e7bd97e83e8be837e3e7afd0a8",
    url = "https://github.com/containernetworking/plugins/releases/download/v0.8.7/cni-plugins-linux-amd64-v0.8.7.tgz",
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
    sha256 = "ffdbeffcdb9865e0c84472c5098d5e1654c14d26e94f9e7e4bcefa6679a181a7",
    url = "https://releases.hashicorp.com/consul/1.10.1/consul_1.10.1_linux_arm64.zip",
)

http_archive(
    name = "consul-template_arm",
    build_file_content = "exports_files([\"consul-template\"])",
    sha256 = "5442dd9ddbd83a1cf059a180681907d74ba7da9bde84c3264bdc7f975190329e",
    url = "https://releases.hashicorp.com/consul-template/0.27.0/consul-template_0.27.0_linux_arm64.zip",
)

http_archive(
    name = "vault_arm",
    build_file_content = "exports_files([\"vault\"])",
    sha256 = "cd2a4cb4b64bb1f9e1a3e4d3227021713c86af2b6e3af227cb96c3b311b30014",
    url = "https://releases.hashicorp.com/vault/1.8.1/vault_1.8.1_linux_arm64.zip",
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
    sha256 = "97a725e3a4b5bcb76c3c67df96df4234ac43e37f9ad4027ebffb1c70905a2190",
    url = "https://releases.hashicorp.com/nomad/1.1.3/nomad_1.1.3_linux_arm64.zip",
)

http_archive(
    name = "terraform_arm",
    build_file_content = "exports_files([\"terraform\"])",
    sha256 = "e34b5274e2fb76d7e6779697304c8f843ee52b523cf212d0bc868c6f4c533ad5",
    url = "https://releases.hashicorp.com/terraform/1.0.5/terraform_1.0.5_linux_arm.zip",
)

http_file(
    name = "terraform-provider-nomad_arm",
    downloaded_file_path = "nomad/terraform-provider-nomad_1.4.15_linux_arm.zip",
    sha256 = "865f198f8e710955f23d7d75f95cb20510574d7931c71b5e8508b785ed52ea3d",
    urls = ["https://releases.hashicorp.com/terraform-provider-nomad/1.4.15/terraform-provider-nomad_1.4.15_linux_arm.zip"],
)

http_file(
    name = "terraform-provider-consul_arm",
    downloaded_file_path = "consul/terraform-provider-consul_2.13.0_linux_arm.zip",
    sha256 = "9a2429febe56c207fe416f905fdfffa4f8984828ae50b46645063efaead6bffa",
    urls = ["https://releases.hashicorp.com/terraform-provider-consul/2.13.0/terraform-provider-consul_2.13.0_linux_arm.zip"],
)

http_file(
    name = "terraform-provider-vault_arm",
    downloaded_file_path = "vault/terraform-provider-vault_2.23.0_linux_arm.zip",
    sha256 = "26122c4b137e9a9747760aa66453af00563e777c806ff1156696db5defada4b0",
    urls = ["https://releases.hashicorp.com/terraform-provider-vault/2.23.0/terraform-provider-vault_2.23.0_linux_arm.zip"],
)

http_archive(
    name = "containernetworking-cni-plugin_arm",
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
    sha256 = "ae13d7b5c05bd180ea9b5b68f44bdaa7bfb41034a2ef1d68fd8e1259797d642f",
    url = "https://github.com/containernetworking/plugins/releases/download/v0.8.7/cni-plugins-linux-arm64-v0.8.7.tgz",
)

### Windows binaries

http_archive(
    name = "consul_win",
    build_file_content = "exports_files([\"consul.exe\"])",
    sha256 = "ebf22aa1bde44dfebf432185ec29e4c8efb6d54ac79b3e9ee6cc5c0ae21ad49a",
    url = "https://releases.hashicorp.com/consul/1.10.1/consul_1.10.1_windows_amd64.zip",
)

http_archive(
    name = "consul-template_win",
    build_file_content = "exports_files([\"consul-template.exe\"])",
    sha256 = "78c38ef2a3ade151e4ffdf4b08c60eb4c72817ddff73830d6c826c562d623cb9",
    url = "https://releases.hashicorp.com/consul-template/0.27.0/consul-template_0.27.0_windows_amd64.zip",
)

http_archive(
    name = "vault_win",
    build_file_content = "exports_files([\"vault.exe\"])",
    sha256 = "130e887a18de9a213418de45af190b95e157dbdbf08a9e2c33d4d53406a8791e",
    url = "https://releases.hashicorp.com/vault/1.8.1/vault_1.8.1_windows_amd64.zip",
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
    sha256 = "0a813f6c72e951b4f322434078e62634037c0bdb73670e47fd72bb35ed843410",
    url = "https://releases.hashicorp.com/nomad/1.1.3/nomad_1.1.3_windows_amd64.zip",
)

http_archive(
    name = "terraform_win",
    build_file_content = "exports_files([\"terraform.exe\"])",
    sha256 = "37de2cd8153286e41b029a719f03b747058cda09576e3297d3d24e1d30e27a12",
    url = "https://releases.hashicorp.com/terraform/1.0.5/terraform_1.0.5_windows_amd64.zip",
)

http_file(
    name = "terraform-provider-nomad_win",
    downloaded_file_path = "nomad/terraform-provider-nomad_1.4.15_linux_win.zip",
    sha256 = "44143738cbdb7defaf180cb59cba3d6ece7e3e2565bd3c8c9fa9b75d38fb53ca",
    urls = ["https://releases.hashicorp.com/terraform-provider-nomad/1.4.15/terraform-provider-nomad_1.4.15_windows_amd64.zip"],
)

http_file(
    name = "terraform-provider-consul_win",
    downloaded_file_path = "consul/terraform-provider-consul_2.13.0_linux_win.zip",
    sha256 = "df10cfd8e6ec28b6e6dd6b4dec7ac4088967793a44ce055dae631dc039c74e10",
    urls = ["https://releases.hashicorp.com/terraform-provider-consul/2.13.0/terraform-provider-consul_2.13.0_windows_amd64.zip"],
)

http_file(
    name = "terraform-provider-vault_win",
    downloaded_file_path = "vault/terraform-provider-vault_2.23.0_linux_win.zip",
    sha256 = "02517cc26a459983154aef6b838e8a04d26d043e90293bc1fda411fcee618836",
    urls = ["https://releases.hashicorp.com/terraform-provider-vault/2.23.0/terraform-provider-vault_2.23.0_windows_amd64.zip"],
)

http_archive(
    name = "containernetworking-cni-plugin_win",
    build_file_content = """
exports_files([
  "flannel.exe",
  "host-local.exe",
  "win-bridge.exe",
  "win-overlay.exe"
])""",
    sha256 = "8dcc56f6856f7df41588bc760244066350d713422019e23573c4be1238a463de",
    url = "https://github.com/containernetworking/plugins/releases/download/v0.8.7/cni-plugins-windows-amd64-v0.8.7.tgz",
)

http_archive(
    name = "rules_jmh",
    strip_prefix = "buchgr-rules_jmh-a5f0231",
    type = "zip",
    url = "https://github.com/buchgr/rules_jmh/zipball/a5f0231ebfde44b4904c7d101b9f269b96c86d06",
    #  sha256 = "dbb7d7e5ec6e932eddd41b910691231ffd7b428dff1ef9a24e4a9a59c1a1762d",
)

load("@rules_jmh//:deps.bzl", "rules_jmh_deps")

rules_jmh_deps()

load("@rules_jmh//:defs.bzl", "rules_jmh_maven_deps")

rules_jmh_maven_deps()

http_archive(
    name = "build_bazel_rules_nodejs",
    sha256 = "f0f76a06fd6c10e8fb9a6cb9389fa7d5816dbecd9b1685063f89fb20dc6822f3",
    urls = ["https://github.com/bazelbuild/rules_nodejs/releases/download/4.5.1/rules_nodejs-4.5.1.tar.gz"],
)

load("@build_bazel_rules_nodejs//:index.bzl", "node_repositories")



node_repositories(
    package_json = ["//interface:package.json"],
    node_version = "16.5.0",
)

load("@build_bazel_rules_nodejs//:index.bzl", "npm_install")

npm_install(
    name = "npm",
    package_json = "//interface:package.json",
    package_lock_json = "//interface:package-lock.json",
)
