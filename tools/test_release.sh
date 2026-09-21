#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/rules-palantir-release.XXXXXX")"
TREEISH="$(git -C "${ROOT}" write-tree)"

cleanup() {
  rm -rf "${TEMP_ROOT}"
}
trap cleanup EXIT

"${ROOT}/tools/release_archive.sh" v0.1.0 "${TEMP_ROOT}/first" "${TREEISH}" >/dev/null
"${ROOT}/tools/release_archive.sh" v0.1.0 "${TEMP_ROOT}/second" "${TREEISH}" >/dev/null
FIRST="${TEMP_ROOT}/first/rules_palantir_java_format-v0.1.0.tar.gz"
SECOND="${TEMP_ROOT}/second/rules_palantir_java_format-v0.1.0.tar.gz"
MANIFEST="${TEMP_ROOT}/archive.manifest"
cmp "${FIRST}" "${SECOND}"

tar -tzf "${FIRST}" >"${MANIFEST}"
if grep -Ev '^rules_palantir_java_format-0\.1\.0/' "${MANIFEST}" >/dev/null; then
  echo "release archive contains an entry outside the versioned prefix" >&2
  exit 1
fi
grep -Fqx 'rules_palantir_java_format-0.1.0/MODULE.bazel' "${MANIFEST}"
grep -Fqx 'rules_palantir_java_format-0.1.0/e2e/smoke/MODULE.bazel' "${MANIFEST}"
tar -xzf "${FIRST}" -C "${TEMP_ROOT}"

BAZEL=(bazel)
BAZEL_COMMON=(--lockfile_mode=error)
if [[ "${RULES_PJF_USE_LOCAL_JDK:-0}" == "1" ]]; then
  BAZEL+=(
    --bazelrc="${TEMP_ROOT}/rules_palantir_java_format-0.1.0/e2e/smoke/.bazelrc"
    --bazelrc="${TEMP_ROOT}/rules_palantir_java_format-0.1.0/tools/local-jdk.bazelrc"
  )
fi
if [[ "${USE_BAZEL_VERSION:-}" == 8.* ]]; then
  BAZEL_COMMON=(--lockfile_mode=off)
fi
cd "${TEMP_ROOT}/rules_palantir_java_format-0.1.0/e2e/smoke"
"${BAZEL[@]}" build "${BAZEL_COMMON[@]}" //:format_check @rules_palantir_java_format//:java_format
