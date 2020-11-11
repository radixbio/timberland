load(
    "//tools:define_scala_targets_with_fmt_phase.bzl",
    original_scala_binary = "scala_binary",
    original_scala_library = "scala_library",
)
load("@io_bazel_rules_scala//scala:scala_import.bzl", "scala_import")
load("@io_bazel_rules_scala//scala:advanced_usage/providers.bzl", "ScalaRulePhase")
load("@io_bazel_rules_scala//scala:advanced_usage/scala.bzl", "make_scala_binary", "make_scala_library")

def external_files_first(file):
    return not file.path.startswith("external")

def _fileToPath(file):
    return file.path

def phase_merge_jars_better(ctx, p):
    """Calls Bazel's singlejar utility.
    For a full list of available command line options see:
    https://github.com/bazelbuild/bazel/blob/697d219526bffbecd29f29b402c9122ec5d9f2ee/src/java_tools/singlejar/java/com/google/devtools/build/singlejar/SingleJar.java#L337
    Use --compression to reduce size of deploy jars.
    """

    deploy_jar = ctx.outputs.deploy_jar
    runtime_jars = sorted(p.compile.rjars.to_list(), key = external_files_first)
    main_class = getattr(ctx.attr, "main_class", "")
    progress_message = "Merging Scala jar: %s" % ctx.label
    args = ctx.actions.args()
    args.add_all(["--compression", "--normalize", "--sources"])
    args.add_all(runtime_jars, map_each = _fileToPath)

    if main_class:
        args.add_all(["--main_class", main_class])
    args.add_all(["--output", deploy_jar.path])

    args.set_param_file_format("multiline")
    args.use_param_file("@%s")
    ctx.actions.run(
        inputs = runtime_jars,
        outputs = [deploy_jar],
        executable = ctx.executable._singlejar,
        mnemonic = "ScalaDeployJar",
        progress_message = progress_message,
        arguments = [args],
    )

def _add_phase_merge_in_correct_order_impl(ctx):
    return [
        ScalaRulePhase(custom_phases = [("replace", "merge_jars", "phase_merge_jars_2", phase_merge_jars_better)]),
    ]

add_phase_merge_in_correct_order = rule(
    _add_phase_merge_in_correct_order_impl,
)
