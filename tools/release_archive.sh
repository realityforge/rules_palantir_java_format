#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TAG="${1:-}"
OUTPUT_DIRECTORY="${2:-${ROOT}}"
TREEISH="${3:-HEAD}"

if [[ ! "${TAG}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "usage: tools/release_archive.sh vMAJOR.MINOR.PATCH [output-directory] [tree-ish]" >&2
  exit 2
fi

VERSION="${TAG#v}"
ARCHIVE="${OUTPUT_DIRECTORY}/rules_palantir_java_format-${TAG}.tar.gz"
mkdir -p "${OUTPUT_DIRECTORY}"
git -C "${ROOT}" archive \
  --format=tar \
  --prefix="rules_palantir_java_format-${VERSION}/" \
  "${TREEISH}" | gzip -n >"${ARCHIVE}"
echo "${ARCHIVE}"
