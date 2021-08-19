#!/bin/bash
echo -ne "\033[0;32m"
echo 'Updating bazel dependencies. This will take about five minutes.'
echo -ne "\033[0m"
set -exu

VERSION="v0.1-7"

if [ "$(uname -s)" == "Linux" ]; then
  BAZEL_DEPS_URL=https://github.com/johnynek/bazel-deps/releases/download/$VERSION/bazel-deps-linux
  #BAZEL_DEPS_SHA256=8597279f17a4cc8d49169276a6266cf1ae342e5700b995c2bd3b83128d028d31
  BAZEL_DEPS_SHA256=$(curl -L --request GET https://github.com/johnynek/bazel-deps/releases/download/$VERSION/bazel-deps-linux.sha256)
elif [ "$(uname -s)" == "Darwin" ]; then
  BAZEL_DEPS_URL=https://github.com/johnynek/bazel-deps/releases/download/$VERSION/bazel-deps-macos
  BAZEL_DEPS_SHA256=$(curl -L --request GET https://github.com/johnynek/bazel-deps/releases/download/$VERSION/bazel-deps-macos.sha256)
else
  echo "Your platform '$(uname -s)' is unsupported, sorry"
  exit 1
fi

# This is some bash snippet designed to find the location of the script.
# we operate under the presumption this script is checked into the repo being operated on
# so we goto the script location, then use git to find the repo root.
SCRIPT_LOCATION="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $SCRIPT_LOCATION

REPO_ROOT=$(git rev-parse --show-toplevel)
echo $REPO_ROOT

if [ -e "${CI:-}" ]; then
    BAZEL_DEPS_DIR="$REPO_ROOT/bazel-deps-cache"
else
    BAZEL_DEPS_DIR="$HOME/.bazel-deps-cache"
fi
BAZEL_DEPS_PATH="${BAZEL_DEPS_DIR}/${VERSION}"

if [ ! -f ${BAZEL_DEPS_PATH} ]; then
  ( # Opens a subshell
    set -e
    echo "Fetching bazel deps."
    curl -L -o /tmp/bazel-deps-bin $BAZEL_DEPS_URL

    GENERATED_SHA_256=$(shasum -a 256 /tmp/bazel-deps-bin | awk '{print $1}')

    if [ "$GENERATED_SHA_256" != "$BAZEL_DEPS_SHA256" ]; then
      echo "Sha 256 does not match, expected: $BAZEL_DEPS_SHA256"
      echo "But found $GENERATED_SHA_256"
      echo "You may need to update the sha in this script, or the download was corrupted."
      exit 1
    fi

    chmod +x /tmp/bazel-deps-bin
    mkdir -p ${BAZEL_DEPS_DIR}
    mv /tmp/bazel-deps-bin ${BAZEL_DEPS_PATH}
  )
fi

cd $REPO_ROOT
set +e
$BAZEL_DEPS_PATH generate -r $REPO_ROOT -s 3rdparty/workspace.bzl -d dependencies.yaml  --target-file 3rdparty/target_file.bzl --disable-3rdparty-in-repo
RET_CODE=$?
set -e

if [ $RET_CODE == 0 ]; then
  echo "Success, going to format files"
else
  echo "Failure, checking out 3rdparty/jvm"
  cd $REPO_ROOT
  git checkout 3rdparty/jvm 3rdparty/workspace.bzl
  exit $RET_CODE
fi

$BAZEL_DEPS_PATH format-deps -d $REPO_ROOT/dependencies.yaml -o
# Rust deps
realpath() {
    path=`eval echo "$1"`
    folder=$(dirname "$path")
    echo $(cd "$folder"; pwd)/$(basename "$path"); 
}
MANIFEST_PATH=$(realpath $REPO_ROOT/Cargo.toml)
touch $REPO_ROOT/3rdparty/crates.bzl
echo -e "def raze_fetch_remote_crates():\n    pass" > $REPO_ROOT/3rdparty/crates.bzl
bazel run @cargo_raze//:raze -- --manifest-path=$MANIFEST_PATH
