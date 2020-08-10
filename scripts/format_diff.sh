#!/bin/bash

git status | egrep "modified|new file" | tr " " "\n" | egrep "scala\$" | xargs -n 1 bazel query | xargs -n 1 -I {} bazel query "attr('srcs', {}, //...)" | sort | uniq | xargs -n 1 -I {} bazel run {}.format
