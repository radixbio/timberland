#!/bin/bash
set -exu
bazel query "kind(scala_library, //...) + kind(scala_binary, //...)" | egrep "^//" | grep -v "//3rdparty" | egrep -v "\.binary\$" | egrep -v ".*jmh_codegen" | egrep -v ".*jmh_generator" | egrep -v ".*-jmh"  | xargs -n 1 -I {} bazel run {}.format

