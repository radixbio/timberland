def workspace_status_impl(ctx):
    output_file = ctx.actions.declare_file("%s.txt" % ctx.attr.name)

    ctx.actions.run_shell(
        inputs = [
            ctx.info_file,
        ],
        outputs = [
            output_file,
        ],
        command = "grep {var} {info} | cut -d ' ' -f 2-  | tee {out}".format(
            var = ctx.attr.variable,
            info = ctx.info_file.path,
            out = output_file.path,
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
        #        "suffix": attr.string( # would be helpful for composing a full release-name.txt including version
        #            mandatory = False,
        #            doc = "Something to put after the variable",
        #            default = ""
        #        )
    },
)
