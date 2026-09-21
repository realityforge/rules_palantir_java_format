# rules_palantir_java_format

Bazel rules and tools for [Palantir Java Format](https://github.com/palantir/palantir-java-format).

This repository is under active development toward its initial `0.1.0` release.

The format check supports ordinary local execution. For persistent single-process reuse, add:

```text
build --strategy=PalantirJavaFormat=worker,local
build --worker_max_instances=PalantirJavaFormat=1
```
