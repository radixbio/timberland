load("//tools:docker.bzl", "dockerize_scala")
load("//tools:terraform.bzl", "terraform_module")

scala_library(
    name = "timberland-svc-lib",
    srcs = ["src/main/scala/com/radix/timberland_svc/timberlandService.scala"],
    visibility = ["//visibility:public"],
    exports = [
        "//timberland/jvm:timberland-service-control",
        "@third_party//3rdparty/jvm/com/lihaoyi:os_lib",
        "@third_party//3rdparty/jvm/org/typelevel:cats_core",
        "@third_party//3rdparty/jvm/org/typelevel:cats_effect",
    ],
    deps = [
        "//timberland/jvm:timberland-service-control",
        "@third_party//3rdparty/jvm/com/lihaoyi:os_lib",
        "@third_party//3rdparty/jvm/org/typelevel:cats_core",
        "@third_party//3rdparty/jvm/org/typelevel:cats_effect",
    ],
)

scala_binary(
    name = "timberland-svc-bin",
    main_class = "com.radix.timberland_svc.timberlandService.timberlandService",
    visibility = ["//timberland/jvm:__subpackages__"],
    deps = [
        ":timberland-svc-lib",
    ],
)
