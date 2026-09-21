#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "${ROOT}"
bazel run //tools/buildifier:buildifier_check
bazel build //...
bazel test //...
bazel run //:java_format -- --root=java_format
git diff --exit-code
