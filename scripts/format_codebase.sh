#!/bin/bash

MONOREPO=$(dirname "$(dirname "$(readlink "$0")")")

CFG=$(cat <<-'EOF'
{
    "query":"kind(scala_library, //...) + kind(scala_binary, //...)",
    "function":"run",
    "args":["--noremote_upload_local_results"],
    "exclude": [
        "//3rdparty.+",
        ".+\\.binary$",
        ".*jmh_codegen",
        ".*jmh_generator",
        ".*-jmh"
    ],
    "format": "{}.format"
}
EOF
)

eval $(python3 "$MONOREPO/scripts/bazel_query_applicator.py" "$CFG")
