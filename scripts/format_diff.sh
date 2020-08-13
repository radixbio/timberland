#!/bin/bash
set -exu
range=${1:-}
changed_targets=$(git diff --name-only $range | grep scala$ | xargs -i bazel query {} --output package 2>/dev/null | sort -u | xargs -i bazel query "kind(scala_, //{}:*)" --output label_kind 2>/dev/null | sort -u | grep "scala_binary\ rule\|scala_library\ rule" | grep -v docker | awk '{print $3}')
for target in $changed_targets
    do bazel run $target.format 2> /dev/null 1>/dev/null
done
