#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TAG="${1:?release tag is required}"

"${ROOT}/tools/release_archive.sh" "${TAG}" "${ROOT}" >/dev/null
echo "Deterministic source archive for ${TAG}."
