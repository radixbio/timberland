def _msi_impl(ctx):
    package_info = [
        ("name", ctx.attr.package),
        ("manufacturer", ctx.attr.manufacturer),
        ("guid", ctx.attr.package_guid),
        ("version", ctx.attr.version),
        ("cabinet_name", ctx.attr.package + ".cab"),
        ("content_base", ctx.attr.data_base_dir),
    ]
    info_string = ",".join(["%s=%s" % (k, v) for k, v in package_info])

    spec_file = ctx.actions.declare_file("%s.wxs" % ctx.attr.package)
    msi_file = ctx.actions.declare_file("%s.msi" % ctx.attr.package)

    # There's also an .exe file made, which is discarded, I guess?
    ctx.actions.run(
        executable = ctx.executable.convert_tar_to_msi,
        inputs = [ctx.file.data, ctx.file.post_install_script],
        outputs = [spec_file, msi_file],
        arguments = [ctx.file.data.path, spec_file.path, msi_file.path, info_string, ctx.file.post_install_script.path],
    )

    return [
        DefaultInfo(files = depset([msi_file, spec_file])),
    ]

pkg_msi = rule(
    _msi_impl,
    attrs = {
        "package": attr.string(),
        "version": attr.string(),
        "description": attr.string(),
        "manufacturer": attr.string(),
        "package_guid": attr.string(),  # GUID used to fingerprint package, should stay the same across versions!
        "data_base_dir": attr.string(),
        "data": attr.label(allow_single_file = True),
        "post_install_script": attr.label(allow_single_file = True),
        "convert_tar_to_msi": attr.label(
            default = Label("//tools:convert_tar_to_msi"),
            cfg = "exec",
            executable = True,
            allow_files = True,
        ),
    },
)
