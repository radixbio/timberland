load("@rules_pkg//:pkg.bzl", "pkg_tar", "pkg_zip")
load("@rules_python//python:defs.bzl", "py_binary")

NomadJobResource = provider(fields = ["jobname", "jobspec_file"])
MainTFSpec = provider(fields = ["specs"])
InstallStructure = provider(fields = ["files"])

def pathjoin(a, b):  # Grah lack of even basic python libraries!
    return a.rstrip("/") + "/" + b.lstrip("/")

def encodeStructure(filestructure):
    return "|".join(["%s*%s" % (targetpath, fileobj.path) for targetpath, fileobj in filestructure])

def rerootStructure(filestructure, rootdir):
    return [(pathjoin(rootdir, target_path), fileobj) for target_path, fileobj in filestructure]

def hash_template(ctx, unhashed_template, dot_git_dir):
    hashed_template = ctx.actions.declare_file(unhashed_template.path)
    ctx.actions.run(
        executable = ctx.executable.mark_image_digests,
        inputs = [unhashed_template, dot_git_dir],
        outputs = [hashed_template],
        arguments = [unhashed_template.path, hashed_template.path, dot_git_dir.path],
    )
    return hashed_template

def _mod_impl(ctx):
    jobname = ctx.attr.jobname

    hashes = []
    if ctx.files.nomad_templates:
        hashes = [hash_template(ctx, file, ctx.file._dot_git_dir) for file in ctx.files.nomad_templates]
    if ctx.file.nomad_template:
        hashes += [hash_template(ctx, ctx.file.nomad_template, ctx.file._dot_git_dir)]

    files = hashes + ctx.files.terraform_files

    mod_deps = [d[NomadJobResource].jobname for d in ctx.attr.deps]
    mod_output_deps = [d[NomadJobResource].jobname for d in ctx.attr.output_deps]

    mod_dep_contents = ""
    if mod_deps:
        mod_dep_contents = ", ".join(["module.%s" % x for x in mod_deps])
    if mod_output_deps:
        mod_dep_contents = ", ".join(["module.%s.%s_health_result" % (x, x) for x in mod_output_deps])
    mod_dep_line = ["depends_on = [%s]" % mod_dep_contents]
    mod_str = 'module "%s" {\n  %s\n}\n' % (jobname, "\n  ".join(mod_dep_line + ctx.attr.module_spec))

    specs = depset([mod_str], transitive = [d[MainTFSpec].specs for d in ctx.attr.deps])
    structure = depset([(pathjoin("modules/%s" % jobname, x.basename), x) for x in files])
    return [
        NomadJobResource(jobname = jobname),
        MainTFSpec(specs = specs),
        InstallStructure(files = structure),
    ]

terraform_module = rule(
    _mod_impl,
    attrs = {
        "jobname": attr.string(),
        "nomad_template": attr.label(allow_single_file = True, default = None),
        "nomad_templates": attr.label_list(allow_files = True, default = []),
        "terraform_files": attr.label_list(allow_files = True, default = []),
        "deps": attr.label_list(default = []),
        "output_deps": attr.label_list(default = []),
        "module_spec": attr.string_list(),
        "mark_image_digests": attr.label(
            default = Label("//tools:annotate_images"),
            cfg = "exec",
            executable = True,
            allow_files = True,
        ),
        "_dot_git_dir": attr.label(
            default = Label("@//:dot-git-dir"),
            allow_single_file = True,
        ),
        "_macos_platform": attr.label(default = Label("@platforms//os:macos")),
    },
)

def _prov_impl(ctx):
    providername = ctx.attr.spec.split('provider "')[1].split('" ')[0]

    sourceWithPath = (
        "/plugins/registry.terraform.io/hashicorp/%s/%s" % (providername, ctx.file.source.basename),
        ctx.file.source,
    )

    return [
        InstallStructure(files = depset([sourceWithPath])),
        #        DefaultInfo(files = depset([tarfile])),
        MainTFSpec(specs = depset([ctx.attr.spec])),
    ]

terraform_provider = rule(
    _prov_impl,
    attrs = {
        "source": attr.label(allow_single_file = True),
        "spec": attr.string(),
        "zipname": attr.string(),
        "_macos_platform": attr.label(default = Label("@platforms//os:macos")),
    },
)

def _render_impl(ctx):
    ter_str = ctx.attr.terraform_spec
    provider_strs = depset([], transitive = [d[MainTFSpec].specs for d in ctx.attr.providers]).to_list()
    module_strs = depset([], transitive = [d[MainTFSpec].specs for d in ctx.attr.modules]).to_list()

    maintf_text = "\n".join([ter_str] + provider_strs + module_strs)
    ctx.actions.write(ctx.outputs.maintf_file, maintf_text)

    return [
        InstallStructure(files = depset([(
            pathjoin("modules", ctx.outputs.maintf_file.basename),
            ctx.outputs.maintf_file,
        )])),
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

def _subdir_impl(ctx):
    return [
        InstallStructure(files = depset([(pathjoin(ctx.attr.dirname, x.basename), x) for x in ctx.files.files])),
    ]

Subdir = rule(
    _subdir_impl,
    attrs = {
        "files": attr.label_list(allow_files = True),
        "dirname": attr.string(),
    },
)

def _ter_impl(ctx):
    files = depset(transitive = [x[InstallStructure].files for x in ctx.attr.deps]).to_list()
    located_files = rerootStructure(files, ctx.attr.module_dir)

    tarfile = ctx.actions.declare_file(ctx.attr.name + ".tar")

    ctx.actions.run(
        executable = ctx.executable.build_structured_tar,
        inputs = [x[1] for x in located_files],
        outputs = [tarfile],
        arguments = [tarfile.path, encodeStructure(located_files)],
    )

    return [
        DefaultInfo(files = depset([tarfile])),
    ]

terraform_rule = rule(
    _ter_impl,
    attrs = {
        "deps": attr.label_list(),
        "module_dir": attr.string(),
        "build_structured_tar": attr.label(
            default = Label("//tools:build_structured_tar"),
            cfg = "exec",
            executable = True,
            allow_files = True,
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
    address   = "consul.service.consul:8501"
    scheme    = "https"
    ca_file   = "/opt/radix/certs/ca/cert.pem"
    cert_file = "/opt/radix/certs/cli/cert.pem"
    key_file  = "/opt/radix/certs/cli/key.pem"
    path      = "terraform"
  }
}""",
        providers = plugin_sources,
        modules = resources,
        maintf_file = "main.tf",
    )

    Subdir(
        name = name + "_toplevel_module_aux_files",
        files = toplevel_module_aux,
        dirname = "modules",
    )

    Subdir(
        name = name + "_terraform_source_file",
        files = terraform_source,
        dirname = "",
    )

    terraform_rule(
        name = name,
        deps = plugin_sources + resources + [
            name + "_maintf",
            name + "_toplevel_module_aux_files",
            name + "_terraform_source_file",
        ],
        module_dir = module_dir,
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
