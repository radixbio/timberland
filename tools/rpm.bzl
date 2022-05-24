def _rpm_pkg_impl(ctx):
    ctx.actions.run(
        executable = ctx.executable._make_rpm_package,
        inputs = [ctx.file.data, ctx.file.spec_template],
        outputs = [ctx.outputs.rpm_file],
        arguments = [
            ctx.file.data.path,
            ctx.file.spec_template.path,
            ctx.outputs.rpm_file.path,
        ],
    )

    return [
        DefaultInfo(files = depset([ctx.outputs.rpm_file])),
    ]

rpm_package = rule(
    _rpm_pkg_impl,
    attrs = {
        "data": attr.label(allow_single_file = True),
        "spec_file": attr.label(allow_single_file = True),
        "spec_template": attr.label(allow_single_file = True),
        "_make_rpm_package": attr.label(
            default = Label("//tools:make_rpm_package"),
            cfg = "exec",
            executable = True,
        ),
        "rpm_file": attr.output(mandatory = True),
    },
)
