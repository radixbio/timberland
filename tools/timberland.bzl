load("@rules_pkg//:pkg.bzl", "pkg_tar")

def _annotate_template_impl(ctx):
    template = ctx.attr.template.files.to_list()[0]
    commit_hash = ctx.attr._commit_hash.files.to_list()[0]
    annotate_images = ctx.attr._annotate_images.files.to_list()[0]
    annotated_template = ctx.actions.declare_file(template.path)
    ctx.actions.run(
        executable = annotate_images,
        inputs = [template, commit_hash],
        outputs = [annotated_template],
        arguments = [
            template.path,
            annotated_template.path,
            commit_hash.path,
        ],
    )
    return [DefaultInfo(files = depset([annotated_template]))]

annotate_template = rule(
    _annotate_template_impl,
    attrs = {
        "template": attr.label(allow_single_file = True),
        "_annotate_images": attr.label(
            default = Label("//tools:annotate_images"),
            cfg = "exec",
            executable = True,
            allow_files = True,
        ),
        "_commit_hash": attr.label(
            default = Label("//timberland:commit-hash"),
            allow_single_file = True,
        ),
    },
)

def timberland_module(name, terraform_srcs = [], jars = []):
    remap_jar_paths = {}
    for src in jars:
        src_file = src.split(":")[-1]
        dest_file = src_file.replace("-pro_slim", "").replace("_deploy", "")
        remap_jar_paths["/" + src_file] = dest_file
    pkg_tar(
        name = name + "-jars",
        srcs = jars,
        package_dir = "/opt/radix/services",
        remap_paths = remap_jar_paths,
    )

    annotated_templates = []
    remap_tf_paths = {}
    for src in terraform_srcs:
        src_file = src.split("/")[-1]
        target_name = name + "-" + src_file
        annotate_template(
            name = target_name,
            template = src,
        )
        annotated_templates.append(target_name)
        remap_tf_paths["/" + target_name] = src_file
    pkg_tar(
        name = name + "-terraform",
        srcs = annotated_templates,
        package_dir = "/opt/radix/timberland/terraform/modules/" + name,
        remap_paths = remap_tf_paths,
        visibility = ["//visibility:public"],  # TODO: Remove once uservice is working
    )

    pkg_tar(
        name = name + "-uservice",
        deps = [name + "-jars", name + "-tffiles"],
        visibility = ["//visibility:public"],
    )
