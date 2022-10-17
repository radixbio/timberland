def _0install_pkg_impl(ctx):
    ctx.actions.run(
        executable = ctx.executable._make_0install_package,
        inputs = [ctx.file.data] + ctx.files.assets,
        outputs = [ctx.outputs.output],
        arguments = [
             ctx.file.data.path,
             ctx.outputs.output.path] + [f.path for f in ctx.files.assets]
    )

    return [
        DefaultInfo(files = depset([ctx.outputs.output])),
    ]

zeroinstall_package = rule(
    _0install_pkg_impl,
    attrs = {
        "assets": attr.label_list(allow_files = True),
        "data": attr.label(allow_single_file = True),
        "_make_0install_package": attr.label(
            default = Label("//tools:make_0install_package"),
            cfg = "exec",
            executable = True,
        ),
        "output": attr.output(mandatory = True),
    },
)
