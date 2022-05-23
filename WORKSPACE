workspace(
    name = "monorepo",
    managed_directories = {"@npm": ["interface/node_modules"]},
)

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

# we need a more up-to-date version of the rules_applecross repository
#git_repository(
#    name = "rules_applecross",
#    commit = "a45de9a402215d0c206b4a74985d8d1c3d0d7524",
#    remote = "https://github.com/apple-cross-toolchain/rules_applecross.git",
#    patch_args = ["-p1"],
#    patches = ["//tools:0002-patch-rules-applecross-to-be-safe-on-macos.patch"],
#    shallow_since = "1635419477 +0900"
#)
#
#http_archive(
#    name = "build_bazel_rules_apple",
#    patch_args = ["-p1"],
#    patches = ["@rules_applecross//third_party:rules_apple.patch"],
#    sha256 = "06191d8c5f87b1f83426cdf6a6d5fc8df545786815801324a1494f46a8a9c3d3",
#    strip_prefix = "rules_apple-0.31.2",
#    url = "https://github.com/bazelbuild/rules_apple/archive/0.31.2.tar.gz",
#)
#
#load("@build_bazel_rules_apple//apple:repositories.bzl", "apple_rules_dependencies")
#
#apple_rules_dependencies()
#
#load(
#    "@rules_applecross//toolchain:apple_cross_toolchain.bzl",
#    "apple_cross_toolchain",
#)
#
#apple_cross_toolchain(
#    name = "apple_cross_toolchain",
#    clang_sha256 = "8f50330cfa4c609841e73286a3a056cff95cf55ec04b3f1280d0cd0052e96c2a",
#    clang_strip_prefix = "clang+llvm-12.0.0-x86_64-linux-gnu-ubuntu-20.04",
#    clang_urls = ["https://github.com/apple-cross-toolchain/ci/releases/download/0.0.6/clang+llvm-12.0.0-x86_64-linux-gnu-ubuntu-20.04-stripped.tar.xz"],
#    swift_sha256 = "869edb04a932c9831922541cb354102244ca33be0aa6325d28b0f14ac0a32a4d",
#    swift_strip_prefix = "swift-5.3.3-RELEASE-ubuntu20.04",
#    swift_urls = ["https://github.com/apple-cross-toolchain/ci/releases/download/0.0.6/swift-5.3.3-RELEASE-ubuntu20.04-stripped.tar.xz"],
#    xcode_sha256 = "44221c0f4acd48d7a33ee7e51143433dee94c649cfee44cfff3c7915ac54fdd2",
#    xcode_urls = ["https://github.com/apple-cross-toolchain/apple-sdks/releases/download/0.0.4/apple-sdks-xcode-12.4.tar.xz"],
#)
#
#load("@apple_cross_toolchain//:repositories.bzl", "apple_cross_toolchain_dependencies")
#
#apple_cross_toolchain_dependencies()
#
#load("@build_bazel_rules_swift//swift:repositories.bzl", "swift_rules_dependencies")
#
#swift_rules_dependencies()
#
#load("@build_bazel_rules_swift//swift:extras.bzl", "swift_rules_extra_dependencies")
#
#swift_rules_extra_dependencies()

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

rules_scala_version = "eabb1d28fb288fb5b15857260f87818dda5a97c8"  # update this as needed

http_archive(
    name = "rules_rust",
    sha256 = "531bdd470728b61ce41cf7604dc4f9a115983e455d46ac1d0c1632f613ab9fc3",
    strip_prefix = "rules_rust-d8238877c0e552639d3e057aadd6bfcf37592408",
    urls = [
        # `main` branch as of 2021-08-23
        "https://github.com/bazelbuild/rules_rust/archive/d8238877c0e552639d3e057aadd6bfcf37592408.tar.gz",
    ],
)

load("@rules_rust//rust:repositories.bzl", "rust_repositories")

rust_repositories(
    edition = "2021",
    version = "1.59.0",
)

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

load("//3rdparty:workspace.bzl", "maven_dependencies")

maven_dependencies()

load("//3rdparty:target_file.bzl", "build_external_workspace")

build_external_workspace(name = "third_party")

load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")

git_repository(
    name = "com_github_johnynek_bazel_jar_jar",
    # Latest commit SHA as at 2019/02/13
    commit = "171f268569384c57c19474b04aebe574d85fde0d",
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

load(
    "@io_bazel_rules_docker//container:container.bzl",
    "container_pull",
)

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
    remote = "git@github.com:radixbio/scalaz3.git",
    #    shallow_since = "1626731854 -0400",
)

http_archive(
    name = "nssm",
    build_file_content = "exports_files([\"nssm-2.24/win32/nssm.exe\"])",
    sha256 = "727d1e42275c605e0f04aba98095c38a8e1e46def453cdffce42869428aa6743",
    url = "https://nssm.cc/release/nssm-2.24.zip",
)

http_archive(
    name = "windows-kill",
    build_file_content = "exports_files([\"windows-kill_x64_1.1.4_lib_release/windows-kill.exe\"])",
    sha256 = "86410dcf5364fb0a26eb3fd3d9c004b8f02ed51e23fcae1107439456ca581ad3",
    url = "https://github.com/ElyDotDev/windows-kill/releases/download/1.1.4/windows-kill_x64_1.1.4_lib_release.zip",
)

http_archive(
    name = "ipfs",
    build_file_content = "exports_files([\"go-ipfs/ipfs\"])",
    sha256 = "aff633d271f642e1c57ce7d1fb3cbf11e50e7e05b35ee17f7373cbb06e519133",
    url = "https://dist.ipfs.io/go-ipfs/v0.9.1/go-ipfs_v0.9.1_linux-amd64.tar.gz",
)

http_archive(
    name = "ipfs_win",
    build_file_content = "exports_files([\"go-ipfs/ipfs.exe\"])",
    sha256 = "17f324a85de057aefe805e882b42dc3e274768ae59fc9f93e5dc89ca92f7e9c0",
    url = "https://dist.ipfs.io/go-ipfs/v0.9.1/go-ipfs_v0.9.1_windows-amd64.zip",
)

# These are all the hashicorp dependencies
# consul, consul-template, vault, vault-secrets-oauthapp, nomad, terraform, terraform-consul, terraform-vault, terraform-nomad
# in the respective CPU architectures (x86, x64, aarch64, aarch43)
# and operating system (linux, windows, macos)
# consul
http_archive(
    name = "consul_linux_x64",
    build_file_content = "exports_files([\"consul\"])",
    sha256 = "109e2077236cae4560b2fa3dce7974ef58d6a7093d72494614d875e5c86e3b2c",
    url = "https://releases.hashicorp.com/consul/1.12.0/consul_1.12.0_linux_amd64.zip",
)

http_archive(
    name = "consul_linux_aarch64",
    build_file_content = "exports_files([\"consul\"])",
    sha256 = "2d22f648af307b63800d291554d3c312beff01d2b4fc8437aeb004935c6bd0cb",
    url = "https://releases.hashicorp.com/consul/1.12.0/consul_1.12.0_linux_arm64.zip",
)

http_archive(
    name = "consul_windows_x64",
    build_file_content = "exports_files([\"consul.exe\"])",
    sha256 = "f5ddbe48fa4c3af1d6463249afb11e39c34a14e8e6e6195ed526baf3431b646c",
    url = "https://releases.hashicorp.com/consul/1.12.0/consul_1.12.0_windows_amd64.zip",
)

http_archive(
    name = "consul_macos_x64",
    build_file_content = "exports_files([\"consul\"])",
    sha256 = "efc169c5f9a07b8a4743686e7159a864c7993746a01caeefc411b9655a59eeec",
    url = "https://releases.hashicorp.com/consul/1.12.0/consul_1.12.0_darwin_amd64.zip",
)

http_archive(
    name = "consul_macos_aarch64",
    build_file_content = "exports_files([\"consul\"])",
    sha256 = "9bc1cd65d83ef8be2a693178d14303e9f0d24b88d5598d003791174a665d3769",
    url = "https://releases.hashicorp.com/consul/1.12.0/consul_1.12.0_darwin_arm64.zip",
)

# consul-template
http_archive(
    name = "consul-template_linux_x64",
    build_file_content = "exports_files([\"consul-template\"])",
    sha256 = "810c6ada4ac9362838f66cf2312dd53d8d51beed37d1c2fb7c3812e1515a9372",
    url = "https://releases.hashicorp.com/consul-template/0.28.0/consul-template_0.28.0_linux_amd64.zip",
)

http_archive(
    name = "consul-template_linux_aarch64",
    build_file_content = "exports_files([\"consul-template\"])",
    sha256 = "b390f80b448b09896e4d634f5c251e44ab897cf67db0e1b78e091ceef50518a0",
    url = "https://releases.hashicorp.com/consul-template/0.28.0/consul-template_0.28.0_linux_arm64.zip",
)

http_archive(
    name = "consul-template_windows_x64",
    build_file_content = "exports_files([\"consul-template.exe\"])",
    sha256 = "93e79a0cb9ba92bca28bfb0dfdb0a2129f15d00632c6dc67c3743ce85aec5dc5",
    url = "https://releases.hashicorp.com/consul-template/0.28.0/consul-template_0.28.0_windows_amd64.zip",
)

http_archive(
    name = "consul-template_macos_x64",
    build_file_content = "exports_files([\"consul-template\"])",
    sha256 = "60f33c4aa3877ee9d2c49146fdc4ae606cc5d8b4aa6f42088dc7fe972f1068a0",
    url = "https://releases.hashicorp.com/consul-template/0.28.0/consul-template_0.28.0_darwin_amd64.zip",
)

# vault
http_archive(
    name = "vault_linux_x64",
    build_file_content = "exports_files([\"vault\"])",
    sha256 = "ec06473d79e77c05700f051278c54b0f7b6f2df64f57f630a0690306323f1175",
    url = "https://releases.hashicorp.com/vault/1.10.0/vault_1.10.0_linux_amd64.zip",
)

http_archive(
    name = "vault_linux_aarch64",
    build_file_content = "exports_files([\"vault\"])",
    sha256 = "e4f963616ed0c4a4a03d541fb531d692014357f2fb53b3c64e75dfe35b96d7be",
    url = "https://releases.hashicorp.com/vault/1.10.0/vault_1.10.0_linux_arm64.zip",
)

http_archive(
    name = "vault_windows_x64",
    build_file_content = "exports_files([\"vault.exe\"])",
    sha256 = "9cf5d22663cc0424b601643523af926e8a6f42a0fad5e81b4a2bbaba286a3669",
    url = "https://releases.hashicorp.com/vault/1.10.0/vault_1.10.0_windows_amd64.zip",
)

http_archive(
    name = "vault_macos_x64",
    build_file_content = "exports_files([\"vault\"])",
    sha256 = "de25ae02c15fa8d0be2871a21c91b9e99495fe4f1c76b245fde300b7dd6a00ad",
    url = "https://releases.hashicorp.com/vault/1.10.0/vault_1.10.0_darwin_amd64.zip",
)

http_archive(
    name = "vault_macos_aarch64",
    build_file_content = "exports_files([\"vault\"])",
    sha256 = "320e7a6927afc611ec004758072c2b6dc053e216236fde0ee9e2a914b5e84db2",
    url = "https://releases.hashicorp.com/vault/1.10.0/vault_1.10.0_darwin_arm64.zip",
)

# vault-plugin-secrets-oauthapp
http_archive(
    name = "vault-plugin-secrets-oauthapp_linux_x64",
    build_file_content = "exports_files([\"vault-plugin-secrets-oauthapp\"])",
    patch_cmds = ["mv vault-plugin-secrets-oauthapp-v1.3.0-linux-amd64 vault-plugin-secrets-oauthapp"],
    sha256 = "5ed0f0df011ede9426fbe59c11ac9d16d0d769c5ed14878ddcf8b931c87fc119",
    url = "https://github.com/puppetlabs/vault-plugin-secrets-oauthapp/releases/download/v1.3.0/vault-plugin-secrets-oauthapp-v1.3.0-linux-amd64.tar.xz",
)

http_archive(
    name = "vault-plugin-secrets-oauthapp_linux_aarch64",
    build_file_content = "exports_files([\"vault-plugin-secrets-oauthapp\"])",
    patch_cmds = ["mv vault-plugin-secrets-oauthapp-v1.3.0-linux-arm64 vault-plugin-secrets-oauthapp"],
    sha256 = "d3d2bb70972d5279a11b6f873d47a65f6221f1ff4637e88644f627ce9c05dd8f",
    url = "https://github.com/puppetlabs/vault-plugin-secrets-oauthapp/releases/download/v1.3.0/vault-plugin-secrets-oauthapp-v1.3.0-linux-arm64.tar.xz",
)

http_archive(
    name = "vault-plugin-secrets-oauthapp_windows_x64",
    build_file_content = "exports_files([\"vault-plugin-secrets-oauthapp.exe\"])",
    patch_cmds = ["mv vault-plugin-secrets-oauthapp-v1.3.0-windows-amd64.exe vault-plugin-secrets-oauthapp.exe"],
    sha256 = "2301d04913dc861f7e0375ae63782ecdf63438c25c4c1616e7e906121c557780",
    url = "https://github.com/puppetlabs/vault-plugin-secrets-oauthapp/releases/download/v1.3.0/vault-plugin-secrets-oauthapp-v1.3.0-windows-amd64.zip",
)

http_archive(
    name = "vault-plugin-secrets-oauthapp_macos_x64",
    build_file_content = "exports_files([\"vault-plugin-secrets-oauthapp\"])",
    patch_cmds = ["mv vault-plugin-secrets-oauthapp-v1.3.0-darwin-amd64 vault-plugin-secrets-oauthapp"],
    url = "https://github.com/puppetlabs/vault-plugin-secrets-oauthapp/releases/download/v1.3.0/vault-plugin-secrets-oauthapp-v1.3.0-darwin-amd64.tar.xz",
)

# nomad
http_archive(
    name = "nomad_linux_x64",
    build_file_content = "exports_files([\"nomad\"])",
    sha256 = "df1f52054a3aaf6db2a564a1bad8bc80902e71746771fe3db18ed4c85cf2c2b1",
    url = "https://releases.hashicorp.com/nomad/1.3.0/nomad_1.3.0_linux_amd64.zip",
)

http_archive(
    name = "nomad_linux_aarch64",
    build_file_content = "exports_files([\"nomad\"])",
    sha256 = "7a68dec9ba9b07bfa143c29ed25c746675c634e60ef550af53dea62fb54769ea",
    url = "https://releases.hashicorp.com/nomad/1.3.0/nomad_1.3.0_linux_arm64.zip",
)

http_archive(
    name = "nomad_windows_x64",
    build_file_content = "exports_files([\"nomad.exe\"])",
    sha256 = "6677ff5b5b034be5b7d1ef4cba19da50817c7382cf2179e4759906e09ee5afb7",
    url = "https://releases.hashicorp.com/nomad/1.3.0/nomad_1.3.0_windows_amd64.zip",
)

http_archive(
    name = "nomad_macos_x64",
    build_file_content = "exports_files([\"nomad\"])",
    sha256 = "80b15bef0af6c16b0488342447542bbb0b2a9e036062dbcf7162bf21f9d235e9",
    url = "https://releases.hashicorp.com/nomad/1.3.0/nomad_1.3.0_darwin_amd64.zip",
)

# terraform
http_archive(
    name = "terraform_linux_x64",
    build_file_content = "exports_files([\"terraform\"])",
    sha256 = "9d2d8a89f5cc8bc1c06cb6f34ce76ec4b99184b07eb776f8b39183b513d7798a",
    url = "https://releases.hashicorp.com/terraform/1.1.9/terraform_1.1.9_linux_amd64.zip",
)

http_archive(
    name = "terraform_linux_aarch64",
    build_file_content = "exports_files([\"terraform\"])",
    sha256 = "e8a09d1fe5a68ed75e5fabe26c609ad12a7e459002dea6543f1084993b87a266",
    url = "https://releases.hashicorp.com/terraform/1.1.9/terraform_1.1.9_linux_arm64.zip",
)

http_archive(
    name = "terraform_windows_x64",
    build_file_content = "exports_files([\"terraform.exe\"])",
    sha256 = "ab4df98d2256a74c151ea7ccfd69a4ad9487b4deba86a61727fb07a1348311cc",
    url = "https://releases.hashicorp.com/terraform/1.1.9/terraform_1.1.9_windows_amd64.zip",
)

http_archive(
    name = "terraform_macos_x64",
    build_file_content = "exports_files([\"terraform\"])",
    sha256 = "c902b3c12042ac1d950637c2dd72ff19139519658f69290b310f1a5924586286",
    url = "https://releases.hashicorp.com/terraform/1.1.9/terraform_1.1.9_darwin_amd64.zip",
)

http_archive(
    name = "terraform_macos_aarch64",
    build_file_content = "exports_files([\"terraform\"])",
    sha256 = "918a8684da5a5529285135f14b09766bd4eb0e8c6612a4db7c121174b4831739",
    url = "https://releases.hashicorp.com/terraform/1.1.9/terraform_1.1.9_darwin_arm64.zip",
)

# terraform-provider-nomad
http_file(
    name = "terraform-provider-nomad_linux_x64",
    downloaded_file_path = "nomad/terraform-provider-nomad_1.4.16_linux_amd64.zip",
    sha256 = "b6260ca9f034df1b47905b4e2a9c33b67dbf77224a694d5b10fb09ae92ffad4c",
    urls = ["https://releases.hashicorp.com/terraform-provider-nomad/1.4.16/terraform-provider-nomad_1.4.16_linux_amd64.zip"],
)

http_file(
    name = "terraform-provider-nomad_windows_x64",
    downloaded_file_path = "nomad/terraform-provider-nomad_1.4.16_windows_amd64.zip",
    sha256 = "7169b8f8df4b8e9659c49043848fd5f7f8473d0471f67815e8b04980f827f5ef",
    urls = ["https://releases.hashicorp.com/terraform-provider-nomad/1.4.16/terraform-provider-nomad_1.4.16_windows_amd64.zip"],
)

http_file(
    name = "terraform-provider-nomad_linux_aarch64",
    downloaded_file_path = "nomad/terraform-provider-nomad_1.4.16_linux_arm64.zip",
    sha256 = "2883b335bb6044b0db6a00e602d6926c047c7f330294a73a90d089f98b24d084",
    urls = ["https://releases.hashicorp.com/terraform-provider-nomad/1.4.16/terraform-provider-nomad_1.4.16_linux_arm64.zip"],
)

http_file(
    name = "terraform-provider-nomad_macos_x64",
    downloaded_file_path = "nomad/terraform-provider-nomad_1.4.16_macos_amd64.zip",
    sha256 = "0db080228e07c72d6d8ca8c45249d6f97cd0189fce82a77abbdcd49a52e57572",
    urls = ["https://releases.hashicorp.com/terraform-provider-nomad/1.4.16/terraform-provider-nomad_1.4.16_darwin_amd64.zip"],
)

http_file(
    name = "terraform-provider-nomad_macos_aarch64",
    downloaded_file_path = "nomad/terraform-provider-nomad_1.4.16_macos_arm64.zip",
    sha256 = "d87c12a6a7768f2b6c2a59495c7dc00f9ecc52b1b868331d4c284f791e278a1e",
    urls = ["https://releases.hashicorp.com/terraform-provider-nomad/1.4.16/terraform-provider-nomad_1.4.16_darwin_arm64.zip"],
)

# terraform-provider-consul
http_file(
    name = "terraform-provider-consul_linux_x64",
    downloaded_file_path = "consul/terraform-provider-consul_2.15.1_linux_amd64.zip",
    sha256 = "c7faa9a2b11bc45833a3e8e340f22f1ecf01597eaeffa7669234b4549d7dfa85",
    urls = ["https://releases.hashicorp.com/terraform-provider-consul/2.15.1/terraform-provider-consul_2.15.1_linux_amd64.zip"],
)

http_file(
    name = "terraform-provider-consul_linux_aarch64",
    downloaded_file_path = "consul/terraform-provider-consul_2.15.1_linux_arm64.zip",
    sha256 = "252be544fb4c9daf09cad7d3776daf5fa66b62740d3ea9d6d499a7b1697c3433",
    urls = ["https://releases.hashicorp.com/terraform-provider-consul/2.15.1/terraform-provider-consul_2.15.1_linux_arm64.zip"],
)

http_file(
    name = "terraform-provider-consul_windows_x64",
    downloaded_file_path = "consul/terraform-provider-consul_2.15.1_windows_amd64.zip",
    sha256 = "896d8ef6d0b555299f124eb25bce8a17d735da14ef21f07582098d301f47da30",
    urls = ["https://releases.hashicorp.com/terraform-provider-consul/2.15.1/terraform-provider-consul_2.15.1_windows_amd64.zip"],
)

http_file(
    name = "terraform-provider-consul_macos_x64",
    downloaded_file_path = "consul/terraform-provider-consul_2.15.1_darwin_amd64.zip",
    sha256 = "1806830a3cf103e65e772a7d28fd4df2788c29a029fb2def1326bc777ad107ed",
    urls = ["https://releases.hashicorp.com/terraform-provider-consul/2.15.1/terraform-provider-consul_2.15.1_darwin_amd64.zip"],
)

http_file(
    name = "terraform-provider-consul_macos_aarch64",
    downloaded_file_path = "consul/terraform-provider-consul_2.15.1_darwin_arm64.zip",
    sha256 = "704f536c621337e06fffef6d5f49ac81f52d249f937250527c12884cb83aefed",
    urls = ["https://releases.hashicorp.com/terraform-provider-consul/2.15.1/terraform-provider-consul_2.15.1_darwin_arm64.zip"],
)

# terraform-provider-vault
http_file(
    name = "terraform-provider-vault_linux_x64",
    downloaded_file_path = "vault/terraform-provider-vault_3.5.0_linux_amd64.zip",
    sha256 = "cd60fe5389f934d860f0eabe96de41898c2332ece8c7270605909ab57fe4fd14",
    urls = ["https://releases.hashicorp.com/terraform-provider-vault/3.5.0/terraform-provider-vault_3.5.0_linux_amd64.zip"],
)

http_file(
    name = "terraform-provider-vault_linux_aarch64",
    downloaded_file_path = "vault/terraform-provider-vault_3.5.0_linux_arm64.zip",
    sha256 = "bcfcbdfce3838741795968b1461391e45309958cf1b8ea6fd2c2c0d1cad6a7e1",
    urls = ["https://releases.hashicorp.com/terraform-provider-vault/3.5.0/terraform-provider-vault_3.5.0_linux_arm64.zip"],
)

http_file(
    name = "terraform-provider-vault_windows_x64",
    downloaded_file_path = "vault/terraform-provider-vault_3.5.0_linux_win.zip",
    sha256 = "31d110c9866cd370bbd730a78a9621a8cdf226ded0f47ce4c02468365a469817",
    urls = ["https://releases.hashicorp.com/terraform-provider-vault/3.5.0/terraform-provider-vault_3.5.0_windows_amd64.zip"],
)

http_file(
    name = "terraform-provider-vault_macos_x64",
    downloaded_file_path = "vault/terraform-provider-vault_3.5.0_macos_amd64.zip",
    sha256 = "417a00c137e2015e24069068240daf1ae4d8f0d866c54594a6a17d1e030cd2cc",
    urls = ["https://releases.hashicorp.com/terraform-provider-vault/3.5.0/terraform-provider-vault_3.5.0_darwin_amd64.zip"],
)

http_file(
    name = "terraform-provider-vault_macos_aarch64",
    downloaded_file_path = "vault/terraform-provider-vault_3.5.0_macos_arm.zip",
    sha256 = "966e508880af89d3e4e4781f90e2f781a6d3d79d2e588ea74f95f2de29bf8df9",
    urls = ["https://releases.hashicorp.com/terraform-provider-vault/3.5.0/terraform-provider-vault_3.5.0_darwin_arm.zip"],
)

# CNI plugins
http_archive(
    name = "containernetworking-cni-plugin_linux_x64",
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
    name = "containernetworking-cni-plugin_linux_aarch64",
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
    name = "containernetworking-cni-plugin_windows_x64",
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
    name = "jaxws",
    build_file_content = "exports_files([\"metro/lib/webservices-tools.jar\"])",
    url = "https://maven.java.net/content/repositories/releases//org/glassfish/metro/metro-standalone/2.3.1/metro-standalone-2.3.1.zip",
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
    node_version = "16.15.0",
    package_json = ["//interface:package.json"],
)

load("@build_bazel_rules_nodejs//:index.bzl", "npm_install")

npm_install(
    name = "npm",
    package_json = "//interface:package.json",
    package_lock_json = "//interface:package-lock.json",
)

load("//containers/packer:deps.bzl", "packer_deps")

packer_deps()
#http_file(
#    name = "macos_monterey",
#    sha256 = "f0f76a06fd6c10e8fb9a6cb9389fa7d5816dbecd9b1685063f89fb20dc6822f3",
#    urls = ["http://oscdn.apple.com/content/downloads/11/43/002-20787/04tih3cmkvpfkgiee5ogbcdx3l4777xzsp/RecoveryImage/BaseSystem.dmg"],
#    auth_patterns = {
#        'Host': 'osrecovery.apple.com',
#        'Connection': 'close',
#        'User-Agent': 'InternetRecovery/1.0',
#        'Cookie': 'session=1643599153~244F4ED4F74A9CA10355CD9FFA853D4A5632D24133A44CD1EFA249BCAB5961ED',
#        'Content-Type': 'text/plain'
#    },
##    build_file_content = "exports_files([\"BaseSystem.dmg\"])",
#)

http_file(
    name = "ubuntu18046_x64",
    sha256 = "f5cbb8104348f0097a8e513b10173a07dbc6684595e331cb06f93f385d0aecf6",
    urls = ["https://cdimage.ubuntu.com/ubuntu/releases/18.04/release/ubuntu-18.04.6-server-amd64.iso"],
)

http_file(
    name = "centos7_x64",
    sha256 = "07b94e6b1a0b0260b94c83d6bb76b26bf7a310dc78d7a9c7432809fb9bc6194a",
    urls = ["https://sjc.edge.kernel.org/centos/7.9.2009/isos/x86_64/CentOS-7-x86_64-Minimal-2009.iso"],
)
