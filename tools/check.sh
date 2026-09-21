#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BAZEL=(bazel)
BAZEL_COMMON=(--lockfile_mode=error)
if [[ "${RULES_PJF_USE_LOCAL_JDK:-0}" == "1" ]]; then
  BAZEL+=(--bazelrc="${ROOT}/.bazelrc" --bazelrc="${ROOT}/tools/local-jdk.bazelrc")
fi
if [[ "${USE_BAZEL_VERSION:-}" == 8.* ]]; then
  BAZEL_COMMON=(--lockfile_mode=off)
fi

cd "${ROOT}"
"${BAZEL[@]}" run "${BAZEL_COMMON[@]}" @buildifier_prebuilt//:buildifier -- -mode=check -lint=warn -r .
"${BAZEL[@]}" build "${BAZEL_COMMON[@]}" //...
"${BAZEL[@]}" test "${BAZEL_COMMON[@]}" --test_output=errors //...
"${BAZEL[@]}" run "${BAZEL_COMMON[@]}" //:java_format -- --root=java_format --root=e2e
"${BAZEL[@]}" mod deps "${BAZEL_COMMON[@]}"
e2e/smoke/test.sh
tools/test_release.sh

if command -v python3 >/dev/null 2>&1; then
  PYTHON=python3
else
  PYTHON=python
fi
"${PYTHON}" -m json.tool .bcr/metadata.template.json >/dev/null
"${PYTHON}" -m json.tool .bcr/source.template.json >/dev/null
grep -Fq 'moduleRoots: ["."]' .bcr/config.yml
grep -Fq 'module_path: "e2e/smoke"' .bcr/presubmit.yml
grep -Fq 'bazel: [8.x, 9.x]' .bcr/presubmit.yml
grep -Fq 'build_targets:' .bcr/presubmit.yml
grep -Fq 'release_ruleset.yaml@v7.7.0' .github/workflows/release.yml
grep -Fq 'publish.yaml@v1.5.0' .github/workflows/publish.yml
git diff --exit-code
