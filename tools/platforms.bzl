# This list should be from smallest to biggest
BUILD_SIZE = ["minimal", "packaging", "full"]
BUILD_ON_OS = ["linux", "macos", "windows"]
BUILD_FOR_OS = ["linux", "debian", "centos", "macos", "windows"]
BUILD_FOR_ARCH = ["x86", "x64", "aarch64", "armv7"]

def generate_constraints():
    PLATFORM_LUT = {
        "linux": ["@platforms//os:linux", "//" + native.package_name() + ":build_for_debian", "//" + native.package_name() + ":build_for_centos"],
        "macos": ["@platforms//os:macos"],
        "windows": ["@platforms//os:windows"],
        "debian": ["@platforms//os:linux", "//" + native.package_name() + ":build_for_linux"],
        "centos": ["@platforms//os:linux", "//" + native.package_name() + ":build_for_linux"],
        "x86": ["@platforms//cpu:x86_32"],
        "x64": ["@platforms//cpu:x86_64"],
        "aarch64": ["@platforms//cpu:aarch64"],
        "armv7": ["@platforms//cpu:armv7"],
    }
    for build_size in BUILD_SIZE:
        native.constraint_setting(
            name = "cs_build_size_" + build_size,
        )
        native.constraint_value(
            name = build_size,
            constraint_setting = ":cs_build_size_" + build_size,
        )
    native.constraint_setting(
        name = "cs_build_on",
    )
    for build_on_os in BUILD_ON_OS:
        build_on = "build_on_" + build_on_os
        native.constraint_value(
            name = build_on,
            constraint_setting = ":cs_build_on",
        )

    for build_for_os in BUILD_FOR_OS:
        native.constraint_setting(
            name = "cs_build_for_" + build_for_os,
        )
        native.constraint_value(
            name = "build_for_" + build_for_os,
            constraint_setting = ":cs_build_for_" + build_for_os,
        )
    native.constraint_setting(
        name = "cs_build_for_arch",
    )
    for build_for_arch in BUILD_FOR_ARCH:
        native.constraint_value(
            name = "build_for_" + build_for_arch,
            constraint_setting = ":cs_build_for_arch",
        )

    for i, build_size in enumerate(BUILD_SIZE):
        # builds in build_size are in size order, so full can build minimal
        super_build_size = ["//" + native.package_name() + ":" + x for x in BUILD_SIZE[:i]]
        for build_on_os in BUILD_ON_OS:
            for build_for_os in BUILD_FOR_OS:
                for build_for_arch in BUILD_FOR_ARCH:
                    cv = [
                        "//" + native.package_name() + ":" + build_size,
                        "//" + native.package_name() + ":build_on_" + build_on_os,
                        "//" + native.package_name() + ":build_for_" + build_for_os,
                        "//" + native.package_name() + ":build_for_" + build_for_arch,
                    ] + PLATFORM_LUT[build_for_os] + PLATFORM_LUT[build_for_arch] + super_build_size
                    native.platform(
                        name = build_size + "_build_on_" + build_on_os + "_build_for_" + build_for_os + "_" + build_for_arch,
                        constraint_values = cv,
                    )
