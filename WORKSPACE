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

load("@io_bazel_rules_docker//container:container.bzl", "container_image", "container_pull")

container_pull(
    name = "custom_java_base",
    digest = "sha256:5075c6b378eaa9fdb9f74c7a33e51c95b0346e78e1469092e0bb29c8a81cbf2e",
    registry = "gcr.io",
    repository = "distroless/java",
)

container_pull(
    name = "alpine_linux_amd64",
    registry = "index.docker.io",
    repository = "library/alpine",
    tag = "3.8",
)

load(
    "@io_bazel_rules_docker//scala:image.bzl",
    _scala_image_repos = "repositories",
)

rules_pkg_dependencies()

_scala_image_repos()

#scala_maven_import_external(
#    name = "com_chuusai_shapeless_sjs0_6_2_12",
#    artifact = "com.chuusai:shapeless_sjs0.6_2.12:2.3.3",
#    licenses = [],
#    server_urls = [
#        "https://repo1.maven.org/maven2/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#    ],
#)
#
#bind(
#    name = "jar/com/chuusai/shapeless_sjs0_6_2_12",
#    actual = "@com_chuusai_shapeless_sjs0_6_2_12//jar",
#)
#
#scala_maven_import_external(
#    name = "com_github_julien_truffaut_monocle_core_sjs0_6_2_12",
#    artifact = "com.github.julien-truffaut:monocle-core_sjs0.6_2.12:1.4.0",
#    licenses = [],
#    server_urls = [
#        "https://repo1.maven.org/maven2/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#    ],
#)
#
#bind(
#    name = "jar/com/github/julien/truffaut/monocle_core_sjs0_6_2_12",
#    actual = "@com_github_julien_truffaut_monocle_core_sjs0_6_2_12//jar",
#)
#
#scala_maven_import_external(
#    name = "com_github_mpilquist_simulacrum_sjs0_6_2_12",
#    artifact = "com.github.mpilquist:simulacrum_sjs0.6_2.12:0.11.0",
#    licenses = [],
#    server_urls = [
#        "https://repo1.maven.org/maven2/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#    ],
#)
#
#bind(
#    name = "jar/com/github/mpilquist/simulacrum_sjs0_6_2_12",
#    actual = "@com_github_mpilquist_simulacrum_sjs0_6_2_12//jar",
#)
#
#scala_maven_import_external(
#    name = "com_lihaoyi_autowire_sjs0_6_2_12",
#    artifact = "com.lihaoyi:autowire_sjs0.6_2.12:0.2.6",
#    licenses = [],
#    server_urls = [
#        "https://repo1.maven.org/maven2/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#    ],
#)
#
#bind(
#    name = "jar/com/lihaoyi/autowire_sjs0_6_2_12",
#    actual = "@com_lihaoyi_autowire_sjs0_6_2_12//jar",
#)
#
#scala_maven_import_external(
#    name = "com_lihaoyi_ujson_sjs0_6_2_12",
#    artifact = "com.lihaoyi:ujson_sjs0.6_2.12:0.6.6",
#    licenses = [],
#    server_urls = [
#        "https://repo1.maven.org/maven2/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#    ],
#)
#
#bind(
#    name = "jar/com/lihaoyi/ujson_sjs0_6_2_12",
#    actual = "@com_lihaoyi_ujson_sjs0_6_2_12//jar",
#)
#
#scala_maven_import_external(
#    name = "com_lihaoyi_upickle_sjs0_6_2_12",
#    artifact = "com.lihaoyi:upickle_sjs0.6_2.12:0.6.6",
#    licenses = [],
#    server_urls = [
#        "https://repo1.maven.org/maven2/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#    ],
#)
#
#bind(
#    name = "jar/com/lihaoyi/upickle_sjs0_6_2_12",
#    actual = "@com_lihaoyi_upickle_sjs0_6_2_12//jar",
#)
#
#scala_maven_import_external(
#    name = "com_slamdata_matryoshka_core_sjs0_6_2_12",
#    artifact = "com.slamdata:matryoshka-core_sjs0.6_2.12:0.21.3",
#    licenses = [],
#    server_urls = [
#        "https://repo1.maven.org/maven2/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#    ],
#)
#
#bind(
#    name = "jar/com/slamdata/matryoshka_core_sjs0_6_2_12",
#    actual = "@com_slamdata_matryoshka_core_sjs0_6_2_12//jar",
#)
#
#scala_maven_import_external(
#    name = "io_circe_circe_core_sjs0_6_2_12",
#    artifact = "io.circe:circe-core_sjs0.6_2.12:0.10.0-M1",
#    licenses = [],
#    server_urls = [
#        "https://repo1.maven.org/maven2/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#    ],
#)
#
#bind(
#    name = "jar/io/circe/circe_core_sjs0_6_2_12",
#    actual = "@io_circe_circe_core_sjs0_6_2_12//jar",
#)
#
#scala_maven_import_external(
#    name = "io_circe_circe_generic_sjs0_6_2_12",
#    artifact = "io.circe:circe-generic_sjs0.6_2.12:0.10.0-M1",
#    licenses = [],
#    server_urls = [
#        "https://repo1.maven.org/maven2/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#    ],
#)
#
#bind(
#    name = "jar/io/circe/circe_generic_sjs0_6_2_12",
#    actual = "@io_circe_circe_generic_sjs0_6_2_12//jar",
#)
#
#scala_maven_import_external(
#    name = "io_circe_circe_numbers_sjs0_6_2_12",
#    artifact = "io.circe:circe-numbers_sjs0.6_2.12:0.10.0-M1",
#    licenses = [],
#    server_urls = [
#        "https://repo1.maven.org/maven2/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#    ],
#)
#
#bind(
#    name = "jar/io/circe/circe_numbers_sjs0_6_2_12",
#    actual = "@io_circe_circe_numbers_sjs0_6_2_12//jar",
#)
#
#scala_maven_import_external(
#    name = "io_circe_circe_parser_sjs0_6_2_12",
#    artifact = "io.circe:circe-parser_sjs0.6_2.12:0.10.0-M1",
#    licenses = [],
#    server_urls = [
#        "https://repo1.maven.org/maven2/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#    ],
#)
#
#bind(
#    name = "jar/io/circe/circe_parser_sjs0_6_2_12",
#    actual = "@io_circe_circe_parser_sjs0_6_2_12//jar",
#)
#
#scala_maven_import_external(
#    name = "io_circe_circe_scalajs_sjs0_6_2_12",
#    artifact = "io.circe:circe-scalajs_sjs0.6_2.12:0.10.0-M1",
#    licenses = [],
#    server_urls = [
#        "https://repo1.maven.org/maven2/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#    ],
#)
#
#bind(
#    name = "jar/io/circe/circe_scalajs_sjs0_6_2_12",
#    actual = "@io_circe_circe_scalajs_sjs0_6_2_12//jar",
#)
#
##scala_maven_import_external(
##    name = "org_apache_avro_avro",
##    artifact = "org.apache.avro:avro:1.8.2",
##    licenses = [],
##    server_urls = [
##        "https://repo1.maven.org/maven2/",
##        "https://oss.sonatype.org/content/repositories/releases",
##        "https://oss.sonatype.org/content/repositories/snapshots",
##        "https://packages.confluent.io/maven/",
##        "https://oss.sonatype.org/content/repositories/releases",
##        "https://oss.sonatype.org/content/repositories/snapshots",
##        "https://packages.confluent.io/maven/",
##    ],
##)
#
##bind(
##    name = "jar/org/apache/avro/avro",
##    actual = "@org_apache_avro_avro//jar",
##)
##
##scala_maven_import_external(
##    name = "org_codehaus_jackson_jackson_core_asl",
##    artifact = "org.codehaus.jackson:jackson-core-asl:1.9.13",
##    licenses = [],
##    server_urls = [
##        "https://repo1.maven.org/maven2/",
##        "https://oss.sonatype.org/content/repositories/releases",
##        "https://oss.sonatype.org/content/repositories/snapshots",
##        "https://packages.confluent.io/maven/",
##        "https://oss.sonatype.org/content/repositories/releases",
##        "https://oss.sonatype.org/content/repositories/snapshots",
##        "https://packages.confluent.io/maven/",
##    ],
##)
##
##bind(
##    name = "jar/org/codehaus/jackson/jackson_core_asl",
##    actual = "@org_codehaus_jackson_jackson_core_asl//jar",
##)
#
##scala_maven_import_external(
##    name = "org_codehaus_jackson_jackson_mapper_asl",
##    artifact = "org.codehaus.jackson:jackson-mapper-asl:1.9.13",
##    licenses = [],
##    server_urls = [
##        "https://repo1.maven.org/maven2/",
##        "https://oss.sonatype.org/content/repositories/releases",
##        "https://oss.sonatype.org/content/repositories/snapshots",
##        "https://packages.confluent.io/maven/",
##        "https://oss.sonatype.org/content/repositories/releases",
##        "https://oss.sonatype.org/content/repositories/snapshots",
##        "https://packages.confluent.io/maven/",
##    ],
##)
#
##bind(
##    name = "jar/org/codehaus/jackson/jackson_mapper_asl",
##    actual = "@org_codehaus_jackson_jackson_mapper_asl//jar",
##)
#
#scala_maven_import_external(
#    name = "org_scala_graph_graph_core_sjs0_6_2_12",
#    artifact = "org.scala-graph:graph-core_sjs0.6_2.12:1.12.5",
#    licenses = [],
#    server_urls = [
#        "https://repo1.maven.org/maven2/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#    ],
#)
#
#bind(
#    name = "jar/org/scala/graph/graph_core_sjs0_6_2_12",
#    actual = "@org_scala_graph_graph_core_sjs0_6_2_12//jar",
#)
#
#scala_maven_import_external(
#    name = "org_scalaz_scalaz_core_sjs0_6_2_12",
#    artifact = "org.scalaz:scalaz-core_sjs0.6_2.12:7.2.15",
#    licenses = [],
#    server_urls = [
#        "https://repo1.maven.org/maven2/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#    ],
#)
#
#bind(
#    name = "jar/org/scalaz/scalaz_core_sjs0_6_2_12",
#    actual = "@org_scalaz_scalaz_core_sjs0_6_2_12//jar",
#)
#
#scala_maven_import_external(
#    name = "org_typelevel_cats_core_sjs0_6_2_12",
#    artifact = "org.typelevel:cats-core_sjs0.6_2.12:1.1.0",
#    licenses = [],
#    server_urls = [
#        "https://repo1.maven.org/maven2/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#    ],
#)
#
#bind(
#    name = "jar/org/typelevel/cats_core_sjs0_6_2_12",
#    actual = "@org_typelevel_cats_core_sjs0_6_2_12//jar",
#)
#
#scala_maven_import_external(
#    name = "org_typelevel_cats_kernel_sjs0_6_2_12",
#    artifact = "org.typelevel:cats-kernel_sjs0.6_2.12:1.1.0",
#    licenses = [],
#    server_urls = [
#        "https://repo1.maven.org/maven2/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#    ],
#)
#
#bind(
#    name = "jar/org/typelevel/cats_kernel_sjs0_6_2_12",
#    actual = "@org_typelevel_cats_kernel_sjs0_6_2_12//jar",
#)
#
#scala_maven_import_external(
#    name = "org_typelevel_cats_macros_sjs0_6_2_12",
#    artifact = "org.typelevel:cats-macros_sjs0.6_2.12:1.1.0",
#    licenses = [],
#    server_urls = [
#        "https://repo1.maven.org/maven2/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#    ],
#)
#
#bind(
#    name = "jar/org/typelevel/cats_macros_sjs0_6_2_12",
#    actual = "@org_typelevel_cats_macros_sjs0_6_2_12//jar",
#)
#
#scala_maven_import_external(
#    name = "org_typelevel_machinist_sjs0_6_2_12",
#    artifact = "org.typelevel:machinist_sjs0.6_2.12:0.6.2",
#    licenses = [],
#    server_urls = [
#        "https://repo1.maven.org/maven2/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#    ],
#)
#
#bind(
#    name = "jar/org/typelevel/machinist_sjs0_6_2_12",
#    actual = "@org_typelevel_machinist_sjs0_6_2_12//jar",
#)
#
#scala_maven_import_external(
#    name = "org_typelevel_squants_sjs0_6_2_12",
#    artifact = "org.typelevel:squants_sjs0.6_2.12:1.3.0",
#    licenses = [],
#    server_urls = [
#        "https://repo1.maven.org/maven2/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#        "https://oss.sonatype.org/content/repositories/releases",
#        "https://oss.sonatype.org/content/repositories/snapshots",
#        "https://packages.confluent.io/maven/",
#    ],
#)
#
#bind(
#    name = "jar/org/typelevel/squants_sjs0_6_2_12",
#    actual = "@org_typelevel_squants_sjs0_6_2_12//jar",
#)
#
#maven_jar(
#    name = "scalajs_ir",
#    artifact = "org.scala-js:scalajs-ir_2.12:0.6.28",
#)
#
#maven_jar(
#    name = "scalajs_tools",
#    artifact = "org.scala-js:scalajs-tools_2.12:0.6.28",
#)

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

load("@io_bazel_rules_docker//container:container.bzl", "container_image", "container_layer", "container_pull", "container_push")

container_pull(
    name = "vault",
    digest = "sha256:0d67bf30e7b1df4a74c7a89e5e3e80f6956417c4962bbda0a8e5472d3766b671",
    registry = "index.docker.io",
    repository = "vault",
    tag = "1.3.0",
)

http_archive(
    name = "consul",
    build_file_content = "exports_files([\"consul\"])",
    sha256 = "5ab689cad175c08a226a5c41d16392bc7dd30ceaaf90788411542a756773e698",
    url = "https://releases.hashicorp.com/consul/1.7.2/consul_1.7.2_linux_amd64.zip",
)

http_archive(
    name = "consul-template",
    build_file_content = "exports_files([\"consul-template\"])",
    sha256 = "496da8d30242ab2804e17ef2fa41aeabd07fd90176986dff58bce1114638bb71",
    url = "https://releases.hashicorp.com/consul-template/0.25.0/consul-template_0.25.0_linux_amd64.zip",
)

http_archive(
    name = "vault",
    build_file_content = "exports_files([\"vault\"])",
    sha256 = "f2bca89cbffb8710265eb03bc9452cc316b03338c411ba8453ffe7419390b8f1",
    url = "https://releases.hashicorp.com/vault/1.4.2/vault_1.4.2_linux_amd64.zip",
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
    sha256 = "b9a266340306f5e8ccbc41b1076250296abb626f7f233c79b70e000e531da509",
    url = "https://releases.hashicorp.com/nomad/0.12.1/nomad_0.12.1_linux_amd64.zip",
)

http_archive(
    name = "terraform",
    build_file_content = "exports_files([\"terraform\"])",
    sha256 = "9ed437560faf084c18716e289ea712c784a514bdd7f2796549c735d439dbe378",
    url = "https://releases.hashicorp.com/terraform/0.13.0/terraform_0.13.0_linux_amd64.zip",
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
    sha256 = "fcfabff53fdef2a8fe5cc7f37b215cd33fcdafd4f3d3e400ddeccdb9c35bf3d5",
    url = "https://releases.hashicorp.com/consul/1.7.2/consul_1.7.2_linux_arm64.zip",
)

http_archive(
    name = "consul-template_arm",
    build_file_content = "exports_files([\"consul-template\"])",
    sha256 = "f89ad4df1beb2b8317e3f9cf3b65b0eddb69d5576736a6b5cb520eee3a206121",
    url = "https://releases.hashicorp.com/consul-template/0.25.0/consul-template_0.25.0_linux_arm64.zip",
)

http_archive(
    name = "vault_arm",
    build_file_content = "exports_files([\"vault\"])",
    sha256 = "bb198bd161479fe0eee649bc6dd2aa82735009bd4f8c341e0676f9112e2376f7",
    url = "https://releases.hashicorp.com/vault/1.4.2/vault_1.4.2_linux_arm64.zip",
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
    sha256 = "89c8995d45a2124e593a304ddde5287ffa4961c31c31ac430f2834b9832a8d73",
    url = "https://releases.hashicorp.com/nomad/0.12.1/nomad_0.12.1_linux_arm64.zip",
)

http_archive(
    name = "terraform_arm",
    build_file_content = "exports_files([\"terraform\"])",
    sha256 = "92a317950e7a308533db8a4a1b33fe7cdba07f3eb33d81bb7def6a2555bcfe62",
    url = "https://releases.hashicorp.com/terraform/0.13.0/terraform_0.13.0_linux_arm.zip",
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
    sha256 = "e9b9355f77f80b2c0940888cb0d27c44a5879c31e379ef21ffcfd36c91d202c1",
    url = "https://releases.hashicorp.com/consul/1.7.2/consul_1.7.2_windows_amd64.zip",
)

http_archive(
    name = "consul-template_win",
    build_file_content = "exports_files([\"consul-template.exe\"])",
    sha256 = "f8626fbe74718d407df78cfbf07392966ef58a32aa41b7a0717eafb4145592ef",
    url = "https://releases.hashicorp.com/consul-template/0.25.0/consul-template_0.25.0_windows_amd64.zip",
)

http_archive(
    name = "vault_win",
    build_file_content = "exports_files([\"vault.exe\"])",
    sha256 = "1e191abe5e8c8fc8682b087a1b5aada23d856a2a6310979efdc49fd595bbbd55",
    url = "https://releases.hashicorp.com/vault/1.4.2/vault_1.4.2_windows_amd64.zip",
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
    sha256 = "597e41e39ca7eb39ef98e90ad9f39e513e97c1d5e8693c66c8cad32e852f77dc",
    url = "https://releases.hashicorp.com/nomad/0.12.1/nomad_0.12.1_windows_amd64.zip",
)

http_archive(
    name = "terraform_win",
    build_file_content = "exports_files([\"terraform.exe\"])",
    sha256 = "8af85914d8804c521152167749ca680d7d51447127deb2c7853835b6c62aa9ed",
    url = "https://releases.hashicorp.com/terraform/0.13.0/terraform_0.13.0_windows_amd64.zip",
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
