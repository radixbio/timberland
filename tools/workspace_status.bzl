def workspace_status_impl(ctx):
    output_file = ctx.actions.declare_file("%s-workspace-status.txt" % ctx.attr.name)

    ctx.actions.run_shell(
        inputs = [
            ctx.info_file,
        ],
        outputs = [
            output_file,
        ],
        command = "grep {} {} | cut -d ' ' -f 2 | {} > {}".format(
            ctx.attr.variable,
            ctx.info_file.path,
            ctx.attr.transformation,
            output_file.path,
        ),
    )
    return [DefaultInfo(files = depset([output_file]))]

workspace_status = rule(
    executable = False,
    implementation = workspace_status_impl,
    attrs = {
        "variable": attr.string(
            mandatory = True,
            doc = "The workspace status variable to write.",
        ),
        "transformation": attr.string(
            mandatory = False,
            doc = "A shell command to transformation the output (e.g. tr '-' '_').",
            default = "cat",
        ),
    },
)
