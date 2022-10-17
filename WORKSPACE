workspace(
    name = "monorepo"
)

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file")
load("@bazel_tools//tools/build_defs/repo:jvm.bzl", "jvm_maven_import_external")
load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository")

skylib_version = "1.0.3"

http_archive(
    name = "bazel_skylib",
    sha256 = "1c531376ac7e5a180e0237938a2536de0c54d93f5c278634818e0efc952dd56c",
    type = "tar.gz",
    url = "https://github.com/bazelbuild/bazel-skylib/releases/download/{}/bazel-skylib-{}.tar.gz".format(skylib_version, skylib_version),
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

rules_scala_version = "4ba3780fcba8d26980daff4639abc6f18517308b"

http_archive(
    name = "io_bazel_rules_scala",
    sha256 = "21f613c25685ec5fdd32f25d0c8ef272cf81636261dda72ce1e99d63c377f0d8",
    strip_prefix = "rules_scala-%s" % rules_scala_version,
    type = "zip",
    url = "https://github.com/bazelbuild/rules_scala/archive/%s.zip" % rules_scala_version,
)

# Stores Scala version and other configuration
# 2.12 is a default version, other versions can be use by passing them explicitly:
# scala_config(scala_version = "2.11.12")
load("@io_bazel_rules_scala//:scala_config.bzl", "scala_config")

scala_config(scala_version = "2.13.8")

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

scala_repositories()

load("@rules_proto//proto:repositories.bzl", "rules_proto_dependencies", "rules_proto_toolchains")

rules_proto_dependencies()

rules_proto_toolchains()

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
    shallow_since = "1596141723 -0400",
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

rules_pkg_dependencies()

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
    sha256 = "abd9a7696e2eeed66fdb28965c220a2ba45ee5cd79ff263557f5392291aab730",
    url = "https://releases.hashicorp.com/consul/1.10.1/consul_1.10.1_linux_amd64.zip",
)

http_archive(
    name = "consul_linux_aarch64",
    build_file_content = "exports_files([\"consul\"])",
    sha256 = "ffdbeffcdb9865e0c84472c5098d5e1654c14d26e94f9e7e4bcefa6679a181a7",
    url = "https://releases.hashicorp.com/consul/1.10.1/consul_1.10.1_linux_arm64.zip",
)

http_archive(
    name = "consul_windows_x64",
    build_file_content = "exports_files([\"consul.exe\"])",
    sha256 = "ebf22aa1bde44dfebf432185ec29e4c8efb6d54ac79b3e9ee6cc5c0ae21ad49a",
    url = "https://releases.hashicorp.com/consul/1.10.1/consul_1.10.1_windows_amd64.zip",
)

http_archive(
    name = "consul_macos_x64",
    build_file_content = "exports_files([\"consul\"])",
    sha256 = "c9a5d2aff56abce89f40d3ab82de03a1bce77b8a37bf90ebeb29aa201e0ac9c1",
    url = "https://releases.hashicorp.com/consul/1.10.1/consul_1.10.1_darwin_amd64.zip",
)

# consul-template
http_archive(
    name = "consul-template_linux_x64",
    build_file_content = "exports_files([\"consul-template\"])",
    sha256 = "9178437bde1f0f220010f17806c01b36ccb3814f065d4ebdfba53ade9832012d",
    url = "https://releases.hashicorp.com/consul-template/0.29.0/consul-template_0.29.0_linux_amd64.zip",
)

http_archive(
    name = "consul-template_linux_aarch64",
    build_file_content = "exports_files([\"consul-template\"])",
    sha256 = "6bbb36d9a47be9f739452147dc4b042bbebdb8fbbaf9a432f1e2a5454478150b",
    url = "https://releases.hashicorp.com/consul-template/0.29.0/consul-template_0.29.0_linux_arm64.zip",
)

http_archive(
    name = "consul-template_windows_x64",
    build_file_content = "exports_files([\"consul-template.exe\"])",
    sha256 = "cc6d00e3a4a121550900db7a54cff217a317d54fae4341eb812431f4e058af1b",
    url = "https://releases.hashicorp.com/consul-template/0.29.0/consul-template_0.29.0_windows_amd64.zip",
)

http_archive(
    name = "consul-template_macos_x64",
    build_file_content = "exports_files([\"consul-template\"])",
    sha256 = "c7110580be052789e8955061de56bf616bdba612ad9ca400fca450912f08fd9d",
    url = "https://releases.hashicorp.com/consul-template/0.29.0/consul-template_0.29.0_darwin_amd64.zip",
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
    patch_cmds = ["mv vault-plugin-secrets-oauthapp-v3.0.0-linux-amd64 vault-plugin-secrets-oauthapp"],
    sha256 = "ceb4b6ab2b54d48b5e34766a09e76c4067bd1a578b93a23d287cd71b9071b7e6",
    url = "https://github.com/puppetlabs/vault-plugin-secrets-oauthapp/releases/download/v3.0.0/vault-plugin-secrets-oauthapp-v3.0.0-linux-amd64.tar.xz",
)

http_archive(
    name = "vault-plugin-secrets-oauthapp_linux_aarch64",
    build_file_content = "exports_files([\"vault-plugin-secrets-oauthapp\"])",
    patch_cmds = ["mv vault-plugin-secrets-oauthapp-v3.0.0-linux-arm64 vault-plugin-secrets-oauthapp"],
    sha256 = "ceb4b6ab2b54d48b5e34766a09e76c4067bd1a578b93a23d287cd71b9071b7e6",
    url = "https://github.com/puppetlabs/vault-plugin-secrets-oauthapp/releases/download/v3.0.0/vault-plugin-secrets-oauthapp-v3.0.0-linux-arm64.tar.xz",
)

http_archive(
    name = "vault-plugin-secrets-oauthapp_windows_x64",
    build_file_content = "exports_files([\"vault-plugin-secrets-oauthapp.exe\"])",
    patch_cmds = ["mv vault-plugin-secrets-oauthapp-v3.0.0-windows-amd64.exe vault-plugin-secrets-oauthapp.exe"],
    sha256 = "cd96942dd7495a9b9af3ae20e00510e7862923124cf9ec4d8f0d5e327bd18d61",
    url = "https://github.com/puppetlabs/vault-plugin-secrets-oauthapp/releases/download/v3.0.0/vault-plugin-secrets-oauthapp-v3.0.0-windows-amd64.zip",
)

http_archive(
    name = "vault-plugin-secrets-oauthapp_macos_x64",
    build_file_content = "exports_files([\"vault-plugin-secrets-oauthapp\"])",
    patch_cmds = ["mv vault-plugin-secrets-oauthapp-v3.0.0-darwin-amd64 vault-plugin-secrets-oauthapp"],
    sha256 = "61838325abefc1b99d3f85aae977e5923d03713203967a26a97100e3d96460f5",
    url = "https://github.com/puppetlabs/vault-plugin-secrets-oauthapp/releases/download/v3.0.0/vault-plugin-secrets-oauthapp-v3.0.0-darwin-amd64.tar.xz",
)

# nomad
http_archive(
    name = "nomad_linux_x64",
    build_file_content = "exports_files([\"nomad\"])",
    sha256 = "d908811cebe2a8373e93c4ad3d09af5c706241878ff3f21ee0f182b4ecb571f2",
    url = "https://releases.hashicorp.com/nomad/1.3.3/nomad_1.3.3_linux_amd64.zip",
)

http_archive(
    name = "nomad_linux_aarch64",
    build_file_content = "exports_files([\"nomad\"])",
    sha256 = "b56e98b22268e21910ffc9039f2bf6dac05bd8b07daa64f9c93739d43fb21991",
    url = "https://releases.hashicorp.com/nomad/1.3.3/nomad_1.3.3_linux_arm64.zip",
)

http_archive(
    name = "nomad_windows_x64",
    build_file_content = "exports_files([\"nomad.exe\"])",
    sha256 = "279bd5e64062719dd48e4704e71e00e966e325c7ab2daa5c9212dde07667b5da",
    url = "https://releases.hashicorp.com/nomad/1.3.3/nomad_1.3.3_windows_amd64.zip",
)

http_archive(
    name = "nomad_macos_x64",
    build_file_content = "exports_files([\"nomad\"])",
    sha256 = "30bf4a8b1cba0e11cf20e4aa49467efe52252942cdb88a1e6cf31bdd069cd020",
    url = "https://releases.hashicorp.com/nomad/1.3.3/nomad_1.3.3_darwin_amd64.zip",
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
    downloaded_file_path = "vault/terraform-provider-vault_3.5.0_macos_arm64.zip",
    sha256 = "966e508880af89d3e4e4781f90e2f781a6d3d79d2e588ea74f95f2de29bf8df9",
    urls = ["https://releases.hashicorp.com/terraform-provider-vault/3.5.0/terraform-provider-vault_3.5.0_darwin_arm64.zip"],
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
    sha256 = "a3392009da4bdd2d50f2bd0731786ddceb48667ce65ff51f28c785d2363efdd3",
    strip_prefix = "buchgr-rules_jmh-a5f0231",
    type = "zip",
    url = "https://github.com/buchgr/rules_jmh/zipball/a5f0231ebfde44b4904c7d101b9f269b96c86d06",
)

load("@rules_jmh//:deps.bzl", "rules_jmh_deps")

rules_jmh_deps()

load("@rules_jmh//:defs.bzl", "rules_jmh_maven_deps")

rules_jmh_maven_deps()

