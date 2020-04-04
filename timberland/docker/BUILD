load("@io_bazel_rules_docker//container:container.bzl", "container_image", "container_layer", "container_pull", "container_push")
load("@io_bazel_rules_docker//docker/package_managers:download_pkgs.bzl", "download_pkgs")
load("@io_bazel_rules_docker//docker/package_managers:install_pkgs.bzl", "install_pkgs")
load("@io_bazel_rules_docker//docker/util:run.bzl", "container_run_and_commit", "container_run_and_extract")
load("@bazel_tools//tools/build_defs/pkg:pkg.bzl", "pkg_deb", "pkg_tar")

#download_pkgs(
#    name = "vault-deps",
#    image_tar = "@vault//image",
#    packages = [
#        "build-base",
#        "git",
#        "go",
#        "make",
#    ],
#)

pkg_tar(
    name = "patch-file",
    srcs = [
        "google.patch",
    ],
)

pkg_tar(
    name = "vault-config",
    srcs = [
        "vault_config.conf",
    ],
)

container_layer(
    name = "vault-files-layer",
    directory = "/usr/src",
    tars = [
        ":patch-file",
    ],
)

container_layer(
    name = "vault-files-layer2",
    directory = "/opt",
    tars = [
        ":vault-config",
    ],
)

container_image(
    name = "modified-vault",
    base = "@vault//image",
    layers = [
        ":vault-files-layer",
        ":vault-files-layer2",
    ],
)

container_run_and_commit(
    name = "radix-vault",
    commands = [
        "apk add git go make build-base",
        "cd /usr/src",
        "git clone https://github.com/puppetlabs/vault-plugin-secrets-oauthapp.git oauth",
        "cp google.patch oauth",
        "cd oauth",
        "patch -p1 < google.patch",
        "make",
        "mkdir /opt/plugins",
        "cp ./bin/vault-plugin-secrets-oauthapp /opt/plugins/",
    ],
    image = ":modified-vault.tar",
)

container_push(
    name = "radix-vault-push-gitlab",
    format = "Docker",
    image = ":radix-vault_commit.tar",
    registry = "docker.aws.radix.bio",
    repository = "timberland/vault",
    tag = "{STABLE_GIT_BRANCH}",
    tags = [
        "auto-push-docker-image"
    ],
)
