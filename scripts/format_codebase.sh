#!/bin/bash
set -exu
bazel query "kind(scala_library, //...) + kind(scala_binary, //...)" | egrep "^//" | grep -v "//3rdparty" | egrep -v "\.binary\$" | xargs -n 1 -I {} bazel run {}.format
