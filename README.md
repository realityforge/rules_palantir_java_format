# rules_palantir_java_format

Bazel rules and tools for [Palantir Java Format](https://github.com/palantir/palantir-java-format). The module owns a
pinned Palantir 2.93.0 dependency graph, so consumers do not need to add formatter artifacts to their Maven setup.

The public API consists of the `java_format_check` rule and the `//:java_format` and `//:java_format_watch`
executables. The module is Bzlmod-only.

## Install

After publication to the Bazel Central Registry, add:

```starlark
bazel_dep(name = "rules_palantir_java_format", version = "0.1.0")
```

Before the initial BCR entry exists, use the immutable GitHub release:

```starlark
bazel_dep(name = "rules_palantir_java_format")

archive_override(
    module_name = "rules_palantir_java_format",
    integrity = "sha256-REPLACE_WITH_RELEASE_INTEGRITY",
    strip_prefix = "rules_palantir_java_format-0.1.0",
    urls = [
        "https://github.com/realityforge/rules_palantir_java_format/releases/download/v0.1.0/rules_palantir_java_format-v0.1.0.tar.gz",
    ],
)
```

Replace the placeholder with the SRI value published with the release. Do not pin a branch or an unreviewed archive.

## Check targets

Load the rule from the root API and point it at Java targets:

```starlark
load("@rules_palantir_java_format//:defs.bzl", "java_format_check")

java_format_check(
    name = "java_format_check",
    remediation = "bazel run @rules_palantir_java_format//:java_format -- --root=src",
    targets = ["//src/main/java:app"],
)
```

The rule checks direct workspace-owned Java sources and follows `deps`, `runtime_deps`, `exports`, and `tests`.
Generated and external sources are excluded. A failure lists workspace-relative files and the exact remediation supplied
by the consumer. Check actions never modify source files.

For single-process worker reuse, add these settings to the consumer's `.bazelrc`:

```text
build --strategy=PalantirJavaFormat=worker,local
build --worker_max_instances=PalantirJavaFormat=1
```

Without these settings, the same check runs correctly as an ordinary local action.

## Write and watch

Both mutation commands require one or more explicit workspace-relative roots:

```console
bazel run @rules_palantir_java_format//:java_format -- --root=src --root=tools
bazel run @rules_palantir_java_format//:java_format_watch -- --root=src --root=tools
```

Roots are normalized, sorted, and deduplicated. Each root must already exist, be a directory inside the workspace, and
contain no symbolic-link component. Only regular `.java` files beneath admitted roots are formatted; symbolic links
are never followed. Repeating a root is safe.

The one-shot command walks all admitted roots deterministically and writes only files whose formatted content differs.
The watcher does not format at startup. It formats eligible create and modify events, registers new directories,
continues after invalid Java input, and performs a bounded admitted-root rescan after an event overflow.

## Compatibility

The `0.1.0` support matrix is intentionally bounded:

| Java | Bazel | Linux x86_64 | macOS x86_64 | macOS arm64 | Windows x86_64 |
| --- | --- | --- | --- | --- | --- |
| 17 | 8, 9 | Supported | Supported | Supported | Supported |
| 21 | 9 | Supported | — | — | — |
| 25 | 9 | Supported | — | — | — |

Later JDK releases are unsupported until they are added to the required CI matrix. Bazel 7 and legacy `WORKSPACE`
integration are not supported.

Before `1.0`, a minor release may make a breaking change to the documented public API. Patch releases preserve the
documented API. Private labels and Java classes may change in any release and must not be consumed directly.

## Troubleshooting

- `usage: java_format ... --root=PATH` means no explicit root was provided.
- `Rejected Java format root` means the path is missing, absolute, outside the workspace, not a directory, or contains
  a symbolic-link component.
- If checks start a JVM per action, add the two worker settings shown above and confirm no broader strategy flag
  overrides the `PalantirJavaFormat` mnemonic.
- If a dirty check names a file but the write command does not repair it, make sure the remediation includes a root
  containing that file.
- JDK 21 and 25 lanes run the Java 17 source baseline on the selected local JDK. Set
  `RULES_PJF_USE_LOCAL_JDK=1 tools/check.sh` to reproduce that configuration.

## Development and releases

Run the complete gate with:

```console
tools/check.sh
```

It checks Buildifier, compilation, unit tests, source formatting, dependency locks, the independent consumer, negative
write/watch behavior, BCR templates, and deterministic release archives. Bazel 8 runs with its Bzlmod lockfile disabled
because its lock schema is incompatible with Bazel 9; the Maven lock is still strict and repository mutation still
fails the gate. A release is created only from an existing
reviewed `vMAJOR.MINOR.PATCH` tag after every required CI lane passes. The release workflow produces the versioned
archive and GitHub provenance attestations.

The Publish-to-BCR workflow is deliberately manual. It requires a `realityforge/bazel-central-registry` fork and a
classic PAT stored as `BCR_PUBLISH_TOKEN`; running it will propose the selected released tag to BCR. No BCR pull
request is part of the `0.1.0` delivery.
