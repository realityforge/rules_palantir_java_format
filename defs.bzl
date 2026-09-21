"""Public rules for checking Java source formatting."""

load("//java_format/private:format.bzl", _java_format_check = "java_format_check")

visibility("public")

def java_format_check(name, targets, remediation, **kwargs):
    """Checks directly owned Java sources reachable from targets.

    Args:
      name: The target name.
      targets: Java targets whose supported dependency edges should be checked.
      remediation: The command users should run when unformatted sources are found.
      **kwargs: Common rule attributes such as visibility and tags.
    """
    _java_format_check(
        name = name,
        remediation = remediation,
        targets = targets,
        **kwargs
    )
