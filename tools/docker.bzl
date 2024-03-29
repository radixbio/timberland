load("@io_bazel_rules_docker//scala:image.bzl", "scala_image")
load("@io_bazel_rules_docker//container:container.bzl", "container_image", "container_push")

def dockerize_scala(
        name,
        srcs = [],
        deps = [],
        base = None,
        main_class = None,
        resources = [],
        repository = None,
        layers = [],
        extra_tar_data = [],
        jvm_flags = [],
        debug = False,
        **kwargs):
    """Creates two rules: scala_image and container_push. Targets are named as follows:
      * scala_image: name + "-docker"
      * container_push: name + "-docker-push"

    When developing locally, the docker image can be imported thusly:
    `bazel run //algs/dbpmjss-uservice/jvm:dbpmjss-uservice-docker -- --norun`

    To actually run the code contained within the image, elide the `--norun` argument.

    When pushing to a remote registry, the docker image will be tagged with the current branch.

    Args:
      name: Base name for all rules.
      srcs: List of source files for scala_binary.
      deps: Dependencies of the scala image target.
      base: Base image to use for scala_image.
      main_class: The main entrypoint class for the scala binary in the scala image.
      resources: Extra files to be included in the image.
      repository: The name of the repository which identifies this image (e.g. algs/foo).
      **kwargs: Applied to all three rules.
    """

    image_name = name + "-docker"
    export_name = image_name + "-push"

    scala_image(
        name = image_name,
        srcs = srcs,
        base = base,
        main_class = main_class,
        deps = deps,
        layers = layers,
        jvm_flags = jvm_flags + ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"] if debug else [],
        **kwargs
    )

    container_image(
        name = image_name + "-wdata",
        base = ":" + image_name,
        tars = extra_tar_data,
    )

    container_push(
        name = export_name,
        format = "Docker",
        image = ":" + image_name + "-wdata",
        registry = "registry.gitlab.com/radix-labs/monorepo",
        repository = repository,
        tag = "{STABLE_GIT_BRANCH}",
        tags = ["auto-push-docker-image"],
        **kwargs
    )
