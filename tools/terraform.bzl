load("@rules_pkg//:pkg.bzl", "pkg_tar", "pkg_zip")
load("@rules_python//python:defs.bzl", "py_binary")

NomadJobResource = provider(fields = ["jobname", "jobspec_file"])
MainTFSpec = provider(fields = ["specs"])

def _ter_impl(ctx):
    jobname = ctx.attr.jobname

    job_tar = ctx.actions.declare_file("%s.tar" % jobname)

    cmd = ("tar --format=gnu -cvhf %s -C %s %s" %
           (job_tar.path, ctx.file.file_dir.dirname, ctx.file.file_dir.basename))

    print("Running: " + cmd)
    ctx.actions.run_shell(
        inputs = [ctx.file.file_dir],
        outputs = [job_tar],
        command = cmd,
    )

    mod_deps = [d[NomadJobResource].jobname for d in ctx.attr.deps]
    if mod_deps:
        mod_dep_line = ["depends_on = [%s]" % ", ".join(["module.%s" % x for x in mod_deps])]
    else:
        mod_dep_line = []
    mod_str = 'module "%s" {\n  %s\n}\n' % (jobname, "\n  ".join(mod_dep_line + ctx.attr.module_spec))

    files = depset([job_tar], transitive = [x.files for x in ctx.attr.deps])
    specs = depset([mod_str], transitive = [d[MainTFSpec].specs for d in ctx.attr.deps])
    return [
        NomadJobResource(jobname = jobname),
        DefaultInfo(files = files),
        MainTFSpec(specs = specs),
    ]

terraform_module = rule(
    _ter_impl,
    attrs = {
        "jobname": attr.string(),
        "file_dir": attr.label(allow_single_file = True),
        "deps": attr.label_list(default = []),
        "module_spec": attr.string_list(),
    },
)

# A complete hack to just (re-)zip the provider executable and then tar it so
# that we can have it in the right directory later
def _prov_impl(ctx):
    providername = ctx.attr.spec.split('provider "')[1].split('" ')[0]
    print(providername)

    tarfile = ctx.actions.declare_file(ctx.file.source.path + ".tar")

    tarcmd = ("tar --format=gnu -cvhf %s -C %s %s" %
              (tarfile.path, ctx.file.source.dirname.strip(providername), providername))
    ctx.actions.run_shell(
        inputs = [ctx.file.source],
        outputs = [tarfile],
        command = tarcmd,
    )

    return [
        DefaultInfo(files = depset([tarfile])),
        MainTFSpec(specs = depset([ctx.attr.spec])),
    ]

terraform_provider = rule(
    _prov_impl,
    attrs = {
        "source": attr.label(allow_single_file = True),
        "spec": attr.string(),
        "zipname": attr.string(),
    },
)

def _render_impl(ctx):
    ter_str = ctx.attr.terraform_spec
    provider_strs = depset([], transitive = [d[MainTFSpec].specs for d in ctx.attr.providers]).to_list()
    module_strs = depset([], transitive = [d[MainTFSpec].specs for d in ctx.attr.modules]).to_list()

    maintf_text = "\n".join([ter_str] + provider_strs + module_strs)
    ctx.actions.write(ctx.outputs.maintf_file, maintf_text)

    return [
        DefaultInfo(files = depset([ctx.outputs.maintf_file])),
    ]

render_main_tf = rule(
    _render_impl,
    attrs = {
        "terraform_spec": attr.string(),
        "providers": attr.label_list(),
        "modules": attr.label_list(),
        "maintf_file": attr.output(
            doc = "the main/main.tf file for this terraform deployment",
            mandatory = True,
        ),
    },
)

def terraform_deployment(
        name,
        terraform_source,
        plugin_sources,
        toplevel_module_aux,
        resources,  # terraform_resource targets
        module_dir):  # Directory where the module gets unpacked
    # It's probably dumb to have separate pkg_tar rules for every
    # subdirectory, TODO better method?

    render_main_tf(
        name = name + "_maintf",
        terraform_spec = """terraform {
  backend "consul" {
    address = "consul.service.consul:8500"
    scheme  = "http"
    path    = "terraform"
  }
}""",
        providers = plugin_sources,
        modules = resources,
        maintf_file = "main.tf",
    )

    pkg_tar(
        name = name + "_maintf_tar",
        srcs = [name + "_maintf"] + toplevel_module_aux,
        package_dir = module_dir + "/modules",
    )

    pkg_tar(
        name = name + "-terraform-exe",
        srcs = [
            terraform_source,
        ],
        package_dir = module_dir,
    )

    pkg_tar(
        name = name + "-terraform-plugins",
        deps = plugin_sources,
        package_dir = module_dir + "/plugins/registry.terraform.io/hashicorp",
    )  # Package dir will have to change/be variable if we start using non-hashicorp providers

    pkg_tar(
        name = name + "-terraform-resources",
        deps = resources,
        package_dir = module_dir + "/modules",
    )

    pkg_tar(
        name = name,
        deps = [
            name + "_maintf_tar",
            name + "-terraform-exe",
            name + "-terraform-plugins",
            name + "-terraform-resources",
        ],
    )

def _dyn_rpm_impl(ctx):
    built_spec_path = ctx.file.base_spec.path.replace(".spec", "_autobuilt.spec")
    built_spec = ctx.actions.declare_file(built_spec_path)

    ctx.actions.run(
        executable = ctx.executable.construct_rpm,
        inputs = [ctx.file.base_spec, ctx.file.tar_archive],
        arguments = [ctx.file.base_spec.path, ctx.file.tar_archive.path, built_spec.path],
        outputs = [built_spec],
    )

    return [DefaultInfo(files = depset([built_spec]))]

dynamic_rpm_spec = rule(
    _dyn_rpm_impl,
    attrs = {
        "base_spec": attr.label(allow_single_file = True),
        "tar_archive": attr.label(allow_single_file = True),
        "construct_rpm": attr.label(
            default = Label("//tools:construct_rpm_spec"),
            cfg = "exec",
            executable = True,
            allow_files = True,
        ),
    },
)
