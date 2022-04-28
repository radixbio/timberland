scala_library(
    name = "timberland-svc-lib",
    srcs = ["src/main/scala/timberlandService.scala"],
    visibility = ["//visibility:public"],
    exports = [
        "//timberland:timberland-lib",
        "@third_party//3rdparty/jvm/com/lihaoyi:os_lib",
        "@third_party//3rdparty/jvm/org/typelevel:cats_core",
        "@third_party//3rdparty/jvm/org/typelevel:cats_effect",
    ],
    deps = [
        "//timberland:timberland-lib",
        "@third_party//3rdparty/jvm/com/lihaoyi:os_lib",
        "@third_party//3rdparty/jvm/org/typelevel:cats_core",
        "@third_party//3rdparty/jvm/org/typelevel:cats_effect",
    ],
)

scala_binary(
    name = "timberland-svc-bin",
    main_class = "com.radix.timberland_svc.timberlandService.timberlandService",
    visibility = ["//timberland:__subpackages__"],
    deps = [
        ":timberland-svc-lib",
    ],
)
