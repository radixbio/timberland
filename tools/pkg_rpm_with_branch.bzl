load("//:tools/workspace_status.bzl", "workspace_status")
load("@rules_pkg//:rpm.bzl", "pkg_rpm")

def pkg_rpm_with_branch(name, **kwargs):
    """Declares a pkg_rpm rule, but sets the version of the RPM to the value of
    STABLE_GIT_BRANCH from the workspace status. See the documentation of the
    --workspace_status_command flag and the pkg_rpm rule for details.

    Note that RPMs do not allow dashes in their version string and will be replaced
    with underscores.

    All arguments passed to this rule get forwarded to pkg_rpm. Do not set the
    version_file attribute, as it is set explicitly inside this rule.
    """
    workspace_status_name = "%s-git-branch" % name

    workspace_status(
        name = workspace_status_name,
        variable = "STABLE_GIT_BRANCH",
        transformation = "tr '-' '_'", # Replace dashes with underscores.
    )

    pkg_rpm(
        name = name,
        version_file = ":%s" % workspace_status_name,
        **kwargs,
    )
