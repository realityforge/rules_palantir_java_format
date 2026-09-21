"""Private aspect and aggregation rule for Java format checks."""

load("@rules_java//java:defs.bzl", "JavaInfo")

visibility("//")

_PalantirJavaFormatInfo = provider(
    "Formatting marker outputs owned by a Java target and its dependencies.",
    fields = {
        "check_outputs": "Formatting marker outputs for this target and traversed dependencies.",
    },
)

_TRAVERSED_ATTRIBUTES = [
    "deps",
    "runtime_deps",
    "exports",
    "tests",
]

def _dependency_outputs(ctx):
    check_outputs = []
    for attribute_name in _TRAVERSED_ATTRIBUTES:
        if not hasattr(ctx.rule.attr, attribute_name):
            continue
        for dependency in getattr(ctx.rule.attr, attribute_name):
            if _PalantirJavaFormatInfo in dependency:
                check_outputs.append(dependency[_PalantirJavaFormatInfo].check_outputs)
    return check_outputs

def _format_aspect_impl(target, ctx):
    transitive_outputs = _dependency_outputs(ctx)
    direct_outputs = []
    direct_sources = []
    if JavaInfo in target and ctx.label.repo_name == "" and hasattr(ctx.rule.files, "srcs"):
        direct_sources = [
            source
            for source in ctx.rule.files.srcs
            if source.is_source and source.basename.endswith(".java")
        ]
    if direct_sources:
        marker = ctx.actions.declare_file(ctx.label.name + ".palantir-java-format")
        arguments = ctx.actions.args()
        arguments.add("--mode=scan")
        arguments.add("--marker=" + marker.path)
        arguments.add_all(direct_sources)
        arguments.set_param_file_format("multiline")
        arguments.use_param_file("@%s", use_always = True)
        ctx.actions.run(
            arguments = [arguments],
            executable = ctx.executable._worker,
            execution_requirements = {
                "requires-worker-protocol": "proto",
                "supports-workers": "1",
            },
            inputs = direct_sources,
            mnemonic = "PalantirJavaFormat",
            outputs = [marker],
            progress_message = "Checking Java format for %{label}",
        )
        direct_outputs = [marker]
    return [
        _PalantirJavaFormatInfo(
            check_outputs = depset(direct = direct_outputs, transitive = transitive_outputs),
        ),
    ]

_palantir_java_format_aspect = aspect(
    implementation = _format_aspect_impl,
    attr_aspects = _TRAVERSED_ATTRIBUTES,
    attrs = {
        "_worker": attr.label(
            default = "//java_format/private/java/org/realityforge/rules/palantirjavaformat:palantir_java_format_worker",
            cfg = "exec",
            executable = True,
        ),
    },
)

def _java_format_check_impl(ctx):
    scan_outputs = depset(transitive = [
        target[_PalantirJavaFormatInfo].check_outputs
        for target in ctx.attr.targets
    ])
    marker = ctx.actions.declare_file(ctx.label.name + ".palantir-java-format")
    arguments = ctx.actions.args()
    arguments.add("--mode=report")
    arguments.add("--marker=" + marker.path)
    arguments.add("--remediation=" + ctx.attr.remediation)
    arguments.add_all(scan_outputs)
    arguments.set_param_file_format("multiline")
    arguments.use_param_file("@%s", use_always = True)
    ctx.actions.run(
        arguments = [arguments],
        executable = ctx.executable._worker,
        execution_requirements = {
            "requires-worker-protocol": "proto",
            "supports-workers": "1",
        },
        inputs = scan_outputs,
        mnemonic = "PalantirJavaFormat",
        outputs = [marker],
        progress_message = "Reporting Java format for %{label}",
    )
    return [
        DefaultInfo(files = depset([marker])),
    ]

java_format_check = rule(
    implementation = _java_format_check_impl,
    attrs = {
        "remediation": attr.string(mandatory = True),
        "targets": attr.label_list(aspects = [_palantir_java_format_aspect]),
        "_worker": attr.label(
            default = "//java_format/private/java/org/realityforge/rules/palantirjavaformat:palantir_java_format_worker",
            cfg = "exec",
            executable = True,
        ),
    },
)
