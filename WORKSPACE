load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:jvm.bzl", "jvm_maven_import_external")
load("@bazel_tools//tools/build_defs/repo:git.bzl", "new_git_repository")

# bazel-skylib 0.8.0 released 2019.03.20 (https://github.com/bazelbuild/bazel-skylib/releases/tag/0.8.0)
skylib_version = "0.8.0"

http_archive(
    name = "bazel_skylib",
    sha256 = "2ef429f5d7ce7111263289644d233707dba35e39696377ebab8b0bc701f7818e",
    type = "tar.gz",
    url = "https://github.com/bazelbuild/bazel-skylib/releases/download/{}/bazel-skylib.{}.tar.gz".format(skylib_version, skylib_version),
)

rules_scala_version = "a676633dc14d8239569affb2acafbef255df3480"  # update this as needed

http_archive(
    name = "io_bazel_rules_scala",
    strip_prefix = "rules_scala-%s" % rules_scala_version,
    type = "zip",
    url = "https://github.com/bazelbuild/rules_scala/archive/%s.zip" % rules_scala_version,
)

http_archive(
    name = "rules_pkg",
    sha256 = "4ba8f4ab0ff85f2484287ab06c0d871dcb31cc54d439457d28fd4ae14b18450a",
    url = "https://github.com/bazelbuild/rules_pkg/releases/download/0.2.4/rules_pkg-0.2.4.tar.gz",
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

scala_repositories((
    "2.12.8",
    {
        "scala_compiler": "f34e9119f45abd41e85b9e121ba19dd9288b3b4af7f7047e86dc70236708d170",
        "scala_library": "321fb55685635c931eba4bc0d7668349da3f2c09aee2de93a70566066ff25c28",
        "scala_reflect": "4d6405395c4599ce04cea08ba082339e3e42135de9aae2923c9f5367e957315a",
        "scalajs_compiler": "fc54c1a5f08598c3aef8b4dd13cf482323b56cb416547da9944655d7c53eae32",
    },
))

#register_toolchains("//:scala_toolchain")

protobuf_version = "09745575a923640154bcf307fba8aedff47f240a"

protobuf_version_sha256 = "416212e14481cff8fd4849b1c1c1200a7f34808a54377e22d7447efdf54ad758"

http_archive(
    name = "com_google_protobuf",
    sha256 = protobuf_version_sha256,
    strip_prefix = "protobuf-%s" % protobuf_version,
    url = "https://github.com/protocolbuffers/protobuf/archive/%s.tar.gz" % protobuf_version,
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

new_git_repository(
    name = "or_tools",
    build_file_content = """
genrule(
    name = "ortools-java-raw",
    srcs = glob(["**"], ["bazel-out/**", "examples/**", "docs/**", "dependencies/archives/**", "dependencies/install/**", "ortools/gen/**", "classes/**", "tools/**", "**/BUILD", "**/*.bzl", "ortools/dotnet/**"]),
    outs = [
        "com.google.ortools.jar",
        "protobuf.jar",
        "libprotobuf.so",
        "libortools.so",
        "libjniortools.so",
        "libglog.so",
        "libgflags.so",
        "libCbcSolver.so",
        "libCbc.so",
        "libClp.so",
        "libOsiClp.so",
        "libCoinUtils.so",
        "libCgl.so",
        "libOsi.so"
    ],
    cmd = "make -j12 -C external/or_tools third_party && make -j12 -C external/or_tools java && cp external/or_tools/lib/libjniortools.so $(location libjniortools.so) && cp external/or_tools/lib/com.google.ortools.jar $(location com.google.ortools.jar) && cp external/or_tools/lib/libortools.so $(location libortools.so) && cp external/or_tools/dependencies/install/lib/libprotobuf.so $(location libprotobuf.so) && cp external/or_tools/dependencies/install/lib/protobuf.jar $(location protobuf.jar) && cp external/or_tools/dependencies/install/lib/libglog.so $(location libglog.so) && cp external/or_tools/dependencies/install/lib/libgflags.so $(location libgflags.so) && cp external/or_tools/dependencies/install/lib/libCbcSolver.so $(location libCbcSolver.so) && cp external/or_tools/dependencies/install/lib/libCbc.so $(location libCbc.so) && cp external/or_tools/dependencies/install/lib/libClp.so $(location libClp.so) && cp external/or_tools/dependencies/install/lib/libOsiClp.so $(location libOsiClp.so) && cp external/or_tools/dependencies/install/lib/libCoinUtils.so $(location libCoinUtils.so) && cp external/or_tools/dependencies/install/lib/libCgl.so $(location libCgl.so) && cp external/or_tools/dependencies/install/lib/libOsi.so $(location libOsi.so)",
)

java_import(
    name = "ortools-java-jar",
    jars = [
        ":com.google.ortools.jar",
        ":protobuf.jar"
    ],
)

java_library(
    name = "ortools-java",
    resources = [
        ":libortools.so",
        ":libjniortools.so",
        ":libprotobuf.so",
        ":libglog.so",
        ":libgflags.so",
        ":libCbcSolver.so",
        ":libCbc.so",
        ":libClp.so",
        ":libOsiClp.so",
        ":libCoinUtils.so",
        ":libCgl.so",
        ":libOsi.so"
    ],
    exports = [
        ":ortools-java-jar"
    ],
    runtime_deps = [
        ":ortools-java-jar"
    ],
    visibility = ["//visibility:public"]
)""",
    commit = "6755c61315c7bb382a558b27cdff7ea2d5970c9c",
    shallow_since = "1570800385 +0200",
    patch_cmds = ["find . -name BUILD | xargs rm"],
    remote = "https://github.com/google/or-tools.git",
)

new_local_repository(
    name = "scalaz3",
    build_file_content = """
genrule(
    name = "scalaz3-raw",
    srcs = glob(["src/**"]) + [
      "project/Build.scala",
      "project/build.properties",
      "project/build.sbt",
      "build.sbt"
    ],
    outs = [
      "libscalaz3.so",
      "libz3.so",
      "scalaz3_2.12-3.0.jar",
    ],
    cmd = "cd external/scalaz3 && sbt --sbt-dir ./.sbt --sbt-boot ./.sbt/boot --ivy ./.ivy2 -mem 4096 +package && cd ../.. && cp external/scalaz3/lib-bin/libscalaz3.so $(location libscalaz3.so) && cp external/scalaz3/z3/z3-4.6.0/build/libz3.so $(location libz3.so) && cp external/scalaz3/target/scala-2.12/scalaz3_2.12-3.0.jar $(location scalaz3_2.12-3.0.jar)",
)

java_import(
    name = "scalaz3-jar",
    jars = [
        ":scalaz3_2.12-3.0.jar",
    ],
)

java_library(
    name = "scalaz3",
    resources = [
        ":libscalaz3.so",
        ":libz3.so",
    ],
    exports = [
        ":scalaz3-jar",
    ],
    runtime_deps = [
        ":scalaz3-jar"
    ],
    visibility = ["//visibility:public"]
)""",
    path = "third-party/ScalaZ3"
)
