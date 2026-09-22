#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FIXTURE="${ROOT}/e2e/smoke"
TEMP_ROOT="$(mktemp -d "${ROOT}/.smoke-test.XXXXXX")"
SMOKE="${TEMP_ROOT}/smoke"
WATCH_PID=""
BAZEL=(bazel)
BAZEL_COMMON=(--lockfile_mode=error)
if [[ "${RULES_PJF_USE_LOCAL_JDK:-0}" == "1" ]]; then
  BAZEL+=(--bazelrc="${FIXTURE}/.bazelrc" --bazelrc="${ROOT}/tools/local-jdk.bazelrc")
fi
if [[ "${USE_BAZEL_VERSION:-}" == 8.* ]]; then
  BAZEL_COMMON=(--lockfile_mode=off)
fi

stop_watcher() {
  if [[ -n "${WATCH_PID}" ]]; then
    case "$(uname -s)" in
      MINGW* | MSYS* | CYGWIN*)
        local windows_pid
        if ! windows_pid="$(jps -l | tr -d '\r' | awk \
          '$2 == "org.realityforge.rules.palantirjavaformat.PalantirJavaFormatWatchMain" { print $1 }')"; then
          echo "Unable to inspect Windows Java processes" >&2
          return 1
        fi
        if [[ ! "${windows_pid}" =~ ^[0-9]+$ ]]; then
          echo "Unable to resolve exactly one Windows watcher JVM PID" >&2
          return 1
        fi
        if ! taskkill.exe //PID "${windows_pid}" //T //F >/dev/null 2>&1; then
          echo "Unable to terminate Windows watcher process tree ${windows_pid}" >&2
          return 1
        fi
        ;;
      *)
        kill "${WATCH_PID}" 2>/dev/null || true
        ;;
    esac
    wait "${WATCH_PID}" 2>/dev/null || true
    WATCH_PID=""
  fi
}

cleanup() {
  stop_watcher || true
  rm -rf "${TEMP_ROOT}"
}
trap cleanup EXIT

fail() {
  echo "smoke test failed: $*" >&2
  exit 1
}

wait_for_content() {
  local file="$1"
  local expected="$2"
  local attempts=0
  while ! grep -Fq "${expected}" "${file}" 2>/dev/null; do
    attempts=$((attempts + 1))
    if [[ ${attempts} -ge 300 ]]; then
      if [[ -f "${file}" ]]; then
        cat "${file}" >&2
      fi
      fail "timed out waiting for '${expected}' in ${file}"
    fi
    sleep 0.1
  done
}

cd "${FIXTURE}"
"${BAZEL[@]}" build "${BAZEL_COMMON[@]}" //:format_check \
  @rules_palantir_java_format//:java_format \
  @rules_palantir_java_format//:java_format_watch

mkdir "${SMOKE}"
cp -R "${FIXTURE}/." "${SMOKE}/"
cd "${SMOKE}"

SOURCE="src/example/Smoke.java"
printf 'package example; class Smoke{}\n' >"${SOURCE}"
BEFORE="$(cksum "${SOURCE}")"
if "${BAZEL[@]}" build "${BAZEL_COMMON[@]}" //:format_check >check.log 2>&1; then
  fail "dirty format check unexpectedly passed"
fi
grep -Fq "src/example/Smoke.java" check.log || fail "dirty check omitted the source path"
grep -Fq "bazel run @rules_palantir_java_format//:java_format -- --root=src" check.log \
  || fail "dirty check omitted remediation"
[[ "${BEFORE}" == "$(cksum "${SOURCE}")" ]] || fail "format check mutated its input"

mkdir excluded
printf 'class Excluded{}\n' >excluded/Excluded.java
"${BAZEL[@]}" run "${BAZEL_COMMON[@]}" @rules_palantir_java_format//:java_format -- --root=src --root=src
grep -Fq 'class Smoke {' "${SOURCE}" || fail "writer did not format an included root"
grep -Fq 'class Excluded{}' excluded/Excluded.java || fail "writer formatted an excluded root"
"${BAZEL[@]}" build "${BAZEL_COMMON[@]}" //:format_check

mkdir watch
printf 'class Existing{}\n' >watch/Existing.java
"${BAZEL[@]}" build "${BAZEL_COMMON[@]}" @rules_palantir_java_format//:java_format_watch
EXECUTION_ROOT="$("${BAZEL[@]}" info "${BAZEL_COMMON[@]}" execution_root)"
WATCHER_OUTPUT="$("${BAZEL[@]}" cquery "${BAZEL_COMMON[@]}" --output=files \
  @rules_palantir_java_format//:java_format_watch | grep -Ev '\.jar$')"
WATCHER="${EXECUTION_ROOT}/${WATCHER_OUTPUT}"
[[ -x "${WATCHER}" ]] || fail "watcher executable was not found at ${WATCHER}"
BUILD_WORKSPACE_DIRECTORY="${SMOKE}" "${WATCHER}" --root=watch >watch.log 2>&1 &
WATCH_PID=$!
wait_for_content watch.log "Watching Java sources under watch"
grep -Fq 'class Existing{}' watch/Existing.java || fail "watcher formatted at startup"
printf 'class Existing{int value;}\n' >watch/Existing.java
wait_for_content watch/Existing.java "class Existing {"
printf 'class Existing {' >watch/Existing.java
wait_for_content watch.log "Unable to format watch/Existing.java"
printf 'class Existing{}\n' >watch/Existing.java
wait_for_content watch/Existing.java "class Existing {}"
mkdir -p watch/nested
printf 'class Nested{}\n' >watch/nested/Nested.java
wait_for_content watch/nested/Nested.java "class Nested {}"
stop_watcher

printf 'class Outside{}\n' >Outside.java
if ln -s "${SMOKE}/Outside.java" watch/Linked.java 2>/dev/null; then
  "${BAZEL[@]}" run "${BAZEL_COMMON[@]}" @rules_palantir_java_format//:java_format -- --root=watch
  grep -Fq 'class Outside{}' Outside.java || fail "writer followed a source symlink"
fi

mkdir visibility
printf '%s\n' \
  'load("@rules_palantir_java_format//java_format/private:format.bzl", "java_format_check")' \
  'java_format_check(name = "bad", remediation = "unused", targets = [])' \
  >visibility/BUILD.bazel
if "${BAZEL[@]}" query "${BAZEL_COMMON[@]}" //visibility:bad >visibility.log 2>&1; then
  fail "private implementation was loadable by a consumer"
fi
grep -Eq 'visibility|not visible' visibility.log || fail "private visibility failure was unclear"
