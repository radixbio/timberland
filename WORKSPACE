load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:jvm.bzl", "jvm_maven_import_external")
load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository", "new_git_repository")

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
    sha256 = "b8b18d0fe3f6c3401b4f83f78f536b24c7fb8b92c593c1dcbcd01cc2b3e85c9a",
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

#bind(
#    name = "io_bazel_rules_scala_dependency_scalatest_scalatest",
#    actual = "//3rdparty/jvm/org/scalatest:scalatest",
#)

scala_repositories(
    scala_extra_jars = {
        "2.12": {
            "scalatest": {
                "version": "3.1.1",
                "sha256": "6412fde52c48ad1c97ff7e8f5b5f4f91b774158d82a80ed8250d2570cee0f83b",
            },
            "scalactic": {
                "version": "3.1.1",
                "sha256": "4fbdce1a3c06823bdb81408c57c85f2fe696a5e5af3bc828022016155c0c5bc4",
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

# Group the sources of the library so that CMake rule have access to it
_all_content = """filegroup(name = "all", srcs = glob(["**"]), visibility = ["//visibility:public"])"""

# Rule repository
http_archive(
    name = "rules_foreign_cc",
    strip_prefix = "rules_foreign_cc-master",
    url = "https://github.com/bazelbuild/rules_foreign_cc/archive/master.zip",
)

http_archive(
    name = "cmake",
    build_file_content = _all_content,
    sha256 = "fc77324c4f820a09052a7785549b8035ff8d3461ded5bbd80d252ae7d1cd3aa5",
    strip_prefix = "cmake-3.17.2",
    urls = [
        "https://github.com/Kitware/CMake/releases/download/v3.17.2/cmake-3.17.2.tar.gz",
    ],
)

load("@rules_foreign_cc//:workspace_definitions.bzl", "rules_foreign_cc_dependencies")

rules_foreign_cc_dependencies(register_default_tools = False)

new_git_repository(
    name = "or_tools",
    build_file_content = """
config_setting(
    name = "darwin",
    constraint_values = ["@bazel_tools//platforms:osx"],
)

config_setting(
    name = "windows",
    constraint_values = ["@bazel_tools//platforms:windows"],
)

config_setting(
    name = "linux",
    constraint_values = ["@bazel_tools//platforms:linux"],
)


genrule(
    name = "ortools-java-raw-mac",
    srcs = glob(["**"], ["bazel-out/**", "examples/**", "docs/**", "dependencies/archives/**", "dependencies/install/**", "ortools/gen/**", "classes/**", "tools/**", "**/BUILD", "**/*.bzl", "ortools/dotnet/**"]),
    outs = [
        "com.google.ortools.mac.jar",
        "protobuf.mac.jar",
        "libprotobuf.3.6.1.dylib",
        "libortools.dylib",
        "libjniortools.dylib",
        "libglog.0.3.5.dylib",
        "libgflags.2.2.dylib",
        "libCbcSolver.3.dylib",
        "libCbc.3.dylib",
        "libClp.1.dylib",
        "libClpSolver.1.dylib",
        "libOsiClp.1.dylib",
        "libOsiCbc.3.dylib",
        "libCoinUtils.3.dylib",
        "libCgl.1.dylib",
        "libOsi.1.dylib"
    ],
    cmd =  " && ".join([
      "sed -i '' 's/jnilib/dylib/' 'external/or_tools/makefiles/Makefile.unix.mk'",
      "make -j $$(nproc) -C external/or_tools third_party 2>/dev/null 1>/dev/null",
      "make -j $$(nproc) -C external/or_tools java 2>/dev/null 1>/dev/null",
      "cp external/or_tools/lib/libjniortools.dylib                          $(location libjniortools.dylib)",
      "cp external/or_tools/lib/com.google.ortools.jar                       $(location com.google.ortools.mac.jar)",
      "cp external/or_tools/lib/libortools.dylib                             $(location libortools.dylib)",
      "cp external/or_tools/dependencies/install/lib/libprotobuf.3.6.1.dylib $(location libprotobuf.3.6.1.dylib)",
      "cp external/or_tools/dependencies/install/lib/protobuf.jar            $(location protobuf.mac.jar)",
      "cp external/or_tools/dependencies/install/lib/libglog.0.3.5.dylib     $(location libglog.0.3.5.dylib)",
      "cp external/or_tools/dependencies/install/lib/libgflags.2.2.dylib     $(location libgflags.2.2.dylib)",
      "cp external/or_tools/dependencies/install/lib/libCbcSolver.3.dylib    $(location libCbcSolver.3.dylib)",
      "cp external/or_tools/dependencies/install/lib/libCbc.3.dylib          $(location libCbc.3.dylib)",
      "cp external/or_tools/dependencies/install/lib/libClp.1.dylib          $(location libClp.1.dylib)",
      "cp external/or_tools/dependencies/install/lib/libClpSolver.1.dylib    $(location libClpSolver.1.dylib)",
      "cp external/or_tools/dependencies/install/lib/libOsiClp.1.dylib       $(location libOsiClp.1.dylib)",
      "cp external/or_tools/dependencies/install/lib/libOsiCbc.3.dylib       $(location libOsiCbc.3.dylib)",
      "cp external/or_tools/dependencies/install/lib/libCoinUtils.3.dylib    $(location libCoinUtils.3.dylib)",
      "cp external/or_tools/dependencies/install/lib/libCgl.1.dylib          $(location libCgl.1.dylib)",
      "cp external/or_tools/dependencies/install/lib/libOsi.1.dylib          $(location libOsi.1.dylib)",
    ]),
    tags = ["requires-network"]

)

genrule(
    name = "ortools-java-raw-linux",
    srcs = glob(["**"], ["bazel-out/**", "examples/**", "docs/**", "dependencies/archives/**", "dependencies/install/**", "ortools/gen/**", "classes/**", "tools/**", "**/BUILD", "**/*.bzl", "ortools/dotnet/**"]),
    outs = [
        "com.google.ortools.linux.jar",
        "protobuf.linux.jar",
        "libprotobuf.so.3.6.1",
        "libortools.so",
        "libjniortools.so",
        "libglog.so.0.3.5",
        "libgflags.so.2.2",
        "libCbcSolver.so.3",
        "libCbc.so.3",
        "libClp.so.1",
        "libOsiClp.so.1",
        "libCoinUtils.so.3",
        "libCgl.so.1",
        "libOsi.so.1"
    ],
    cmd =  " && ".join([
      "make -j $$(nproc) -C external/or_tools third_party 2>/dev/null 1>/dev/null",
      "make -j $$(nproc) -C external/or_tools java 2>/dev/null 1>/dev/null",
      "cp external/or_tools/lib/libjniortools.so                     $(location libjniortools.so)",
      "cp external/or_tools/lib/com.google.ortools.jar               $(location com.google.ortools.linux.jar)",
      "cp external/or_tools/lib/libortools.so                        $(location libortools.so)",
      "if [ -f external/or_tools/dependencies/install/lib64/libprotobuf.so.3.6.1 ]; then cp external/or_tools/dependencies/install/lib64/libprotobuf.so.3.6.1 $(location libprotobuf.so.3.6.1); else cp external/or_tools/dependencies/install/lib/libprotobuf.so.3.6.1 $(location libprotobuf.so.3.6.1); fi",
      "cp external/or_tools/dependencies/install/lib/protobuf.jar    $(location protobuf.linux.jar)",
      "if [ -f external/or_tools/dependencies/install/lib64/libglog.so.0.3.5 ]; then cp external/or_tools/dependencies/install/lib64/libglog.so.0.3.5 $(location libglog.so.0.3.5); else cp external/or_tools/dependencies/install/lib/libglog.so.0.3.5 $(location libglog.so.0.3.5); fi",
      "cp external/or_tools/dependencies/install/lib/libgflags.so.2.2    $(location libgflags.so.2.2)",
      "cp external/or_tools/dependencies/install/lib/libCbcSolver.so.3 $(location libCbcSolver.so.3)",
      "cp external/or_tools/dependencies/install/lib/libCbc.so.3       $(location libCbc.so.3)",
      "cp external/or_tools/dependencies/install/lib/libClp.so.1       $(location libClp.so.1)",
      "cp external/or_tools/dependencies/install/lib/libOsiClp.so.1    $(location libOsiClp.so.1)",
      "cp external/or_tools/dependencies/install/lib/libCoinUtils.so.3 $(location libCoinUtils.so.3)",
      "cp external/or_tools/dependencies/install/lib/libCgl.so.1       $(location libCgl.so.1)",
      "cp external/or_tools/dependencies/install/lib/libOsi.so.1       $(location libOsi.so.1)",
    ]),
    tags = ["requires-network"]
)

java_import(
    name = "ortools-java-jar",
    jars = select({":linux": [
        ":com.google.ortools.linux.jar",
        ":protobuf.linux.jar"
    ],
     ":darwin": [":com.google.ortools.mac.jar",
     ":protobuf.mac.jar"]})
)

java_library(
    name = "ortools-java",
    resources = select({
    ":darwin" : [
        ":libortools.dylib",
        ":libjniortools.dylib",
        ":libprotobuf.3.6.1.dylib",
        ":libglog.0.3.5.dylib",
        ":libgflags.2.2.dylib",
        ":libCbcSolver.3.dylib",
        ":libCbc.3.dylib",
        ":libClp.1.dylib",
        ":libClpSolver.1.dylib",
        ":libOsiClp.1.dylib",
        ":libOsiCbc.3.dylib",
        ":libCoinUtils.3.dylib",
        ":libCgl.1.dylib",
        ":libOsi.1.dylib"
    ],
    ":linux" : [
        ":libortools.so",
        ":libjniortools.so",
        ":libprotobuf.so.3.6.1",
        ":libglog.so.0.3.5",
        ":libgflags.so.2.2",
        ":libCbcSolver.so.3",
        ":libCbc.so.3",
        ":libClp.so.1",
        ":libOsiClp.so.1",
        ":libCoinUtils.so.3",
        ":libCgl.so.1",
        ":libOsi.so.1"
    ]
    }),
    exports = [
        ":ortools-java-jar"
    ],
    runtime_deps = [
        ":ortools-java-jar"
    ],
    visibility = ["//visibility:public"]
)""",
    commit = "07a142892e960093ad277a6b7b95a1ee3b162d48",
    patch_cmds = ["find . -name BUILD | xargs rm"],
    remote = "https://github.com/google/or-tools.git",
    shallow_since = "1550848918 +0100",
)

git_repository(
    name = "scalaz3",
    commit = "173f23079e899b496fbd91ac93b9861551adb113",
    remote = "git@gitlab.com:radix-labs/scalaz3.git",
    shallow_since = "1582754945 -0500",
)

new_git_repository(
    name = "z3",
    build_file_content = """

config_setting(
    name = "darwin",
    constraint_values = ["@bazel_tools//platforms:osx"],
)

config_setting(
    name = "windows",
    constraint_values = ["@bazel_tools//platforms:windows"],
)

config_setting(
    name = "linux",
    constraint_values = ["@bazel_tools//platforms:linux"],
)

package(default_visibility = ["//visibility:public"])
load("@//:tools/cmake_build.bzl", "cmake_tool")
cmake_tool(
    name = "cmaketool",
    cmake_srcs = "@cmake//:all",
)
genrule(
    name = "z3-raw-linux",
    srcs = glob(
        ["**"],
        exclude = [
            "bazel-bin/**",
            "bazel-out/**",
            "bazel-testlogs/**",
            "bazel-z3/**",
            "BUILD",
            "WORKSPACE",
            "cmake-build-debug/**",
            ".bazelrc",
            "**/*.pyc",
            ".git/**",
            "**/*.swp",
            ".*",
            "**/*.class",
            "build/*",
        ],
    ),
    outs = [
        "libz3.so.4.8",
        "libz3java.so",
        "com.microsoft.z3.linux.jar",
        "z3.linux.h",
        "z3java.linux.h",
        "z3_macros.linux.h",
        "z3_api.linux.h",
        "z3_ast_containers.linux.h",
        "z3_algebraic.linux.h",
        "z3_polynomial.linux.h",
        "z3_rcf.linux.h",
        "z3_fixedpoint.linux.h",
        "z3_optimization.linux.h",
        "z3_fpa.linux.h",
        "z3_spacer.linux.h",
    ],
    tools = [":cmaketool"],
    cmd = " && ".join(
        [
            "export INTERNAL_ROOT_DIR=$$(pwd)",
            "if ! [ -d \\"$(GENDIR)/staging\\" ]; then mkdir $(GENDIR)/staging; fi",
            "if ! [ -d \\"$(GENDIR)/staging/enums\\" ]; then mkdir $(GENDIR)/staging/enums; fi",
            "if ! [ -d \\"$(GENDIR)/jni\\" ]; then mkdir $(GENDIR)/jni; fi",
            "cd external/z3",
            "export INTERNAL_BUILD_DIR=$$(pwd)",
            "export JAVA_HOME=$$(readlink -f /usr/bin/javac | sed \\"s:/bin/javac::\\")",
	    "mkdir build",
	    "cd build",
            "$$INTERNAL_ROOT_DIR/bazel-out/host/bin/external/z3/cmake/bin/cmake -DCMAKE_BUILD_TYPE=Release -DZ3_ENABLE_TRACING_FOR_NON_DEBUG=FALSE -DZ3_BUILD_JAVA_BINDINGS=TRUE -DZ3_BUILD_LIBZ3_SHARED=TRUE -DZ3_LINK_TIME_OPTIMIZATION=TRUE -DCMAKE_BUILD_RPATH_USE_ORIGIN=TRUE ../",
            "make -j $$(nproc) 1>/dev/null 2>/dev/null",
	    "cp src/api/java/Native.java $$INTERNAL_ROOT_DIR/$(GENDIR)/staging",
	    "cp src/api/java/Native.cpp $$INTERNAL_ROOT_DIR/$(GENDIR)/staging",
	    "cp src/api/java/enumerations/*.java $$INTERNAL_ROOT_DIR/$(GENDIR)/staging/enums",
            "cd $$INTERNAL_ROOT_DIR",
	    "cp $$INTERNAL_BUILD_DIR/src/api/java/Z3Exception.java $(GENDIR)/staging",
            "cp $$INTERNAL_BUILD_DIR/build/com.microsoft.z3.jar $(location com.microsoft.z3.linux.jar)",
            "cp $$INTERNAL_BUILD_DIR/build/libz3.so.4.8 $(location libz3.so.4.8)",
            "cp $$INTERNAL_BUILD_DIR/build/libz3java.so $(location libz3java.so)",
            "cp $$INTERNAL_BUILD_DIR/src/api/z3.h $(location z3.linux.h)",
            "cp $$INTERNAL_BUILD_DIR/src/api/z3_macros.h        $(location z3_macros.linux.h)",
            "cp $$INTERNAL_BUILD_DIR/src/api/z3_api.h           $(location z3_api.linux.h)",
            "cp $$INTERNAL_BUILD_DIR/src/api/z3_ast_containers.h $(location z3_ast_containers.linux.h)",
            "cp $$INTERNAL_BUILD_DIR/src/api/z3_algebraic.h     $(location z3_algebraic.linux.h)",
            "cp $$INTERNAL_BUILD_DIR/src/api/z3_polynomial.h    $(location z3_polynomial.linux.h)",
            "cp $$INTERNAL_BUILD_DIR/src/api/z3_rcf.h           $(location z3_rcf.linux.h)",
            "cp $$INTERNAL_BUILD_DIR/src/api/z3_fixedpoint.h    $(location z3_fixedpoint.linux.h)",
            "cp $$INTERNAL_BUILD_DIR/src/api/z3_optimization.h  $(location z3_optimization.linux.h)",
            "cp $$INTERNAL_BUILD_DIR/src/api/z3_fpa.h           $(location z3_fpa.linux.h)",
            "cp $$INTERNAL_BUILD_DIR/src/api/z3_spacer.h        $(location z3_spacer.linux.h)",
            "/usr/bin/javac -h $(GENDIR)/jni/ $(GENDIR)/staging/Native.java $(GENDIR)/staging/Z3Exception.java $(GENDIR)/staging/enums/*.java",
            "cp $(GENDIR)/jni/com_microsoft_z3_Native.h $(location z3java.linux.h)",

        ],
    ),
    visibility = ["//visibility:public"],
)
genrule(
    name = "z3-raw-mac",
    srcs = glob(
        ["**"],
        exclude = [
            "bazel-bin/**",
            "bazel-out/**",
            "bazel-testlogs/**",
            "bazel-z3/**",
            "BUILD",
            "WORKSPACE",
            "cmake-build-debug/**",
            ".bazelrc",
            "**/*.pyc",
            ".git/**",
            "**/*.swp",
            ".*",
            "**/*.class",
            "build/*",
        ],
    ),
    outs = [
        "libz3.4.8.dylib",
        "libz3java.dylib",
        "com.microsoft.z3.mac.jar",
        "z3.mac.h",
        "z3java.mac.h",
        "z3_macros.mac.h",
        "z3_api.mac.h",
        "z3_ast_containers.mac.h",
        "z3_algebraic.mac.h",
        "z3_polynomial.mac.h",
        "z3_rcf.mac.h",
        "z3_fixedpoint.mac.h",
        "z3_optimization.mac.h",
        "z3_fpa.mac.h",
        "z3_spacer.mac.h",
    ],
    tools = [":cmaketool"],
    cmd = " && ".join(
        [
            "export INTERNAL_ROOT_DIR=$$(pwd)",
            "if ! [ -d \\"$(GENDIR)/staging\\" ]; then mkdir $(GENDIR)/staging; fi",
            "if ! [ -d \\"$(GENDIR)/staging/enums\\" ]; then mkdir $(GENDIR)/staging/enums; fi",
            "if ! [ -d \\"$(GENDIR)/jni\\" ]; then mkdir $(GENDIR)/jni; fi",
            "cd external/z3",
            "export INTERNAL_BUILD_DIR=$$(pwd)",
            "export JAVA_HOME=$$(readlink -f /usr/bin/javac | sed \\"s:/bin/javac::\\")",
	    "mkdir build",
	    "cd build",
            "$$INTERNAL_ROOT_DIR/bazel-out/host/bin/external/z3/cmake/bin/cmake -DCMAKE_BUILD_TYPE=Release -DZ3_ENABLE_TRACING_FOR_NON_DEBUG=FALSE -DZ3_BUILD_JAVA_BINDINGS=TRUE -DZ3_BUILD_LIBZ3_SHARED=TRUE -DZ3_LINK_TIME_OPTIMIZATION=TRUE -DCMAKE_BUILD_RPATH_USE_ORIGIN=TRUE ../",
            "make -j $$(nproc) 1>/dev/null 2>/dev/null",
	    "cp src/api/java/Native.java $$INTERNAL_ROOT_DIR/$(GENDIR)/staging",
	    "cp src/api/java/Native.cpp $$INTERNAL_ROOT_DIR/$(GENDIR)/staging",
	    "cp src/api/java/enumerations/*.java $$INTERNAL_ROOT_DIR/$(GENDIR)/staging/enums",
            "cd $$INTERNAL_ROOT_DIR",
	    "cp $$INTERNAL_BUILD_DIR/src/api/java/Z3Exception.java $(GENDIR)/staging",
            "cp $$INTERNAL_BUILD_DIR/build/com.microsoft.z3.jar $(location com.microsoft.z3.mac.jar)",
            "cp $$INTERNAL_BUILD_DIR/build/libz3.4.8.dylib $(location libz3.4.8.dylib)",
            "cp $$INTERNAL_BUILD_DIR/build/libz3java.dylib $(location libz3java.dylib)",
            "cp $$INTERNAL_BUILD_DIR/src/api/z3.h $(location z3.mac.h)",
            "cp $$INTERNAL_BUILD_DIR/src/api/z3_macros.h        $(location z3_macros.mac.h)",
            "cp $$INTERNAL_BUILD_DIR/src/api/z3_api.h           $(location z3_api.mac.h)",
            "cp $$INTERNAL_BUILD_DIR/src/api/z3_ast_containers.h $(location z3_ast_containers.mac.h)",
            "cp $$INTERNAL_BUILD_DIR/src/api/z3_algebraic.h     $(location z3_algebraic.mac.h)",
            "cp $$INTERNAL_BUILD_DIR/src/api/z3_polynomial.h    $(location z3_polynomial.mac.h)",
            "cp $$INTERNAL_BUILD_DIR/src/api/z3_rcf.h           $(location z3_rcf.mac.h)",
            "cp $$INTERNAL_BUILD_DIR/src/api/z3_fixedpoint.h    $(location z3_fixedpoint.mac.h)",
            "cp $$INTERNAL_BUILD_DIR/src/api/z3_optimization.h  $(location z3_optimization.mac.h)",
            "cp $$INTERNAL_BUILD_DIR/src/api/z3_fpa.h           $(location z3_fpa.mac.h)",
            "cp $$INTERNAL_BUILD_DIR/src/api/z3_spacer.h        $(location z3_spacer.mac.h)",
            "/usr/bin/javac -h $(GENDIR)/jni/ $(GENDIR)/staging/Native.java $(GENDIR)/staging/Z3Exception.java $(GENDIR)/staging/enums/*.java",
            "cp $(GENDIR)/jni/com_microsoft_z3_Native.h $(location z3java.mac.h)",

        ],
    ),
    visibility = ["//visibility:public"],
)

java_import(
    name = "z3-jar",
    jars = select({":linux" : [":com.microsoft.z3.linux.jar"], ":darwin" : [":com.microsoft.z3.mac.jar"]}),
)

cc_import(
    name = "z3_import_versioned",
    hdrs = select({":linux": [
        ":z3.linux.h",
        ":z3_algebraic.linux.h",
        ":z3_api.linux.h",
        ":z3_ast_containers.linux.h",
        ":z3_fixedpoint.linux.h",
        ":z3_fpa.linux.h",
        ":z3_macros.linux.h",
        ":z3_optimization.linux.h",
        ":z3_polynomial.linux.h",
        ":z3_rcf.linux.h",
        ":z3_spacer.linux.h",
    ],
    ":darwin": [
        ":z3.mac.h",
        ":z3_algebraic.mac.h",
        ":z3_api.mac.h",
        ":z3_ast_containers.mac.h",
        ":z3_fixedpoint.mac.h",
        ":z3_fpa.mac.h",
        ":z3_macros.mac.h",
        ":z3_optimization.mac.h",
        ":z3_polynomial.mac.h",
        ":z3_rcf.mac.h",
        ":z3_spacer.mac.h",
    ]}),
    shared_library = select({":linux": ":libz3.so.4.8", ":darwin" : ":libz3.4.8.dylib"}),
)

cc_import(
    name = "z3java",
    hdrs = select({":linux" :[
        ":z3java.linux.h",
    ],
    ":darwin" : [":z3java.mac.h"]}),
    shared_library =  select({":linux": ":libz3java.so", ":darwin" : ":libz3java.dylib"})
)
""",
    commit = "30e7c225cd510400eacd41d0a83e013b835a8ece",
    remote = "https://github.com/Z3Prover/z3.git",
    shallow_since = "1574197124 -0800",
    workspace_file_content = """
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
_all_content = \"\"\"filegroup(name = "all", srcs = glob(["**"]), visibility = ["//visibility:public"])\"\"\"
# Rule repository
http_archive(
   name = "rules_foreign_cc",
   strip_prefix = "rules_foreign_cc-master",
   url = "https://github.com/bazelbuild/rules_foreign_cc/archive/master.zip",
)
#vendored cmake (required)
http_archive(
    name = "cmake",
    build_file_content = _all_content,
    sha256 = "fc77324c4f820a09052a7785549b8035ff8d3461ded5bbd80d252ae7d1cd3aa5",
    strip_prefix = "cmake-3.17.2",
    urls = [
        "https://github.com/Kitware/CMake/releases/download/v3.17.2/cmake-3.17.2.tar.gz",
    ],
)
load("@rules_foreign_cc//:workspace_definitions.bzl", "rules_foreign_cc_dependencies")
rules_foreign_cc_dependencies(register_default_tools = False)
    """,
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
    name = "nomad",
    build_file_content = "exports_files([\"nomad\"])",
    sha256 = "711e98b89ac4f5540bf3d6273379999f6c4141529531c262222e63ce491f5176",
    url = "https://releases.hashicorp.com/nomad/0.11.1/nomad_0.11.1_linux_amd64.zip",
)

http_archive(
    name = "terraform",
    build_file_content = "exports_files([\"terraform\"])",
    sha256 = "602d2529aafdaa0f605c06adb7c72cfb585d8aa19b3f4d8d189b42589e27bf11",
    url = "https://releases.hashicorp.com/terraform/0.12.24/terraform_0.12.24_linux_amd64.zip",
)

http_archive(
    name = "terraform-provider-consul",
    build_file_content = "exports_files([\"terraform-provider-consul_v2.7.0_x4\"])",
    sha256 = "6cc007f02065258d2e7e9d04d196aa7c16731f11f5923e06d78488e14be2c8a5",
    url = "https://releases.hashicorp.com/terraform-provider-consul/2.7.0/terraform-provider-consul_2.7.0_linux_amd64.zip",
)

http_archive(
    name = "terraform-provider-nomad",
    build_file_content = "exports_files([\"terraform-provider-nomad_v1.4.7_x4\"])",
    sha256 = "d5aa264f5b92c61305822e368631417e2b8dc4feed29e2d6f16b36a36333a380",
    url = "https://releases.hashicorp.com/terraform-provider-nomad/1.4.7/terraform-provider-nomad_1.4.7_linux_amd64.zip",
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
