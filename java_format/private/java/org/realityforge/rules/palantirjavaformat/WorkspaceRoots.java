package org.realityforge.rules.palantirjavaformat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

final class WorkspaceRoots {
    private WorkspaceRoots() {}

    static List<String> parse(final String[] args) {
        final Set<String> roots = new LinkedHashSet<>();
        for (final String argument : args) {
            if (!argument.startsWith("--root=") || "--root=".equals(argument)) {
                throw new IllegalArgumentException("usage: java_format --root=PATH [--root=PATH ...]");
            }
            roots.add(argument.substring("--root=".length()));
        }
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("usage: java_format --root=PATH [--root=PATH ...]");
        }
        return roots.stream().sorted().toList();
    }

    static List<Path> resolve(final Path workspace, final List<String> rootNames) throws IOException {
        final Path realWorkspace = workspace.toRealPath();
        final Set<Path> roots = new LinkedHashSet<>();
        for (final String rootName : rootNames) {
            final Path relative = Path.of(rootName);
            if (relative.isAbsolute()) {
                throw rejected(rootName);
            }
            final Path candidate = realWorkspace.resolve(relative).normalize();
            if (!candidate.startsWith(realWorkspace)
                    || !Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)
                    || hasSymbolicLinkComponent(realWorkspace, candidate)) {
                throw rejected(rootName);
            }
            final Path realRoot = candidate.toRealPath();
            if (!realRoot.startsWith(realWorkspace)) {
                throw rejected(rootName);
            }
            roots.add(realRoot);
        }
        return roots.stream().sorted(Comparator.comparing(Path::toString)).toList();
    }

    static List<Path> discoverJavaSources(final List<Path> roots) throws IOException {
        final List<Path> sources = new ArrayList<>();
        for (final Path root : roots) {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .filter(path -> path.getFileName().toString().endsWith(".java"))
                        .forEach(sources::add);
            }
        }
        sources.sort(Comparator.comparing(Path::toString));
        return List.copyOf(new LinkedHashSet<>(sources));
    }

    static boolean hasSymbolicLinkComponent(final Path workspace, final Path path) {
        if (!path.startsWith(workspace)) {
            return true;
        }
        Path current = workspace;
        for (final Path component : workspace.relativize(path)) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                return true;
            }
        }
        return false;
    }

    private static IllegalArgumentException rejected(final String root) {
        return new IllegalArgumentException("Rejected Java format root: " + root);
    }
}
