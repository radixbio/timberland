load("@io_bazel_rules_docker//scala:image.bzl", "scala_image")
load("@io_bazel_rules_docker//container:container.bzl", "container_push")

def dockerize_scala(
    name,
    srcs = [],
    deps = [],
    base = None,
    main_class = None,
    resources = [],
    repository = None,
    **kwargs):
  """Creates three rules: scala_image, container_push, and a genrule for importing the docker image
  in to the developer's local store. Targets are named as follows:
    * scala_image: name + "-docker"
    * container_push: name + "-docker-push"
    * genrule: name + "-docker-import"

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

  scala_image(
    name = image_name,
    srcs = srcs,
    base = base,
    main_class = main_class,
    deps = deps,
    **kwargs
  )

  container_push(
    name = image_name + "-push",
    format = "Docker",
    image = ":" + image_name,
    registry = "docker.aws.radix.bio",
    repository = repository,
    tag = "{STABLE_GIT_BRANCH}",
    tags = [ "auto-push-docker-image" ],
    **kwargs
  )

  native.genrule(
    name = image_name + "-import",
    srcs = [ ":" + image_name ],
    outs = [ "load.sh" ],
    cmd_bash = "echo \"docker import $(SRCS)\" " + repository + ":dev > $(OUTS)",
    executable = True,
    **kwargs
  )
