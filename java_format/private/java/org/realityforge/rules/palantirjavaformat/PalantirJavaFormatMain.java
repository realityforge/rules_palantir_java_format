package org.realityforge.rules.palantirjavaformat;

import com.palantir.javaformat.java.FormatterException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PalantirJavaFormatMain {
    private PalantirJavaFormatMain() {}

    public static void main(final String[] args) throws IOException, FormatterException {
        System.exit(run(args, System.getenv(), System.err));
    }

    static int run(final String[] args, final Map<String, String> environment, final PrintStream error)
            throws IOException, FormatterException {
        final String workspace = environment.get("BUILD_WORKSPACE_DIRECTORY");
        if (null == workspace || workspace.isBlank()) {
            error.println("BUILD_WORKSPACE_DIRECTORY is not set");
            return 2;
        }
        try {
            final Arguments arguments = parse(args);
            formatWorkspace(Path.of(workspace), arguments.rootNames(), arguments.fileNames(), new PalantirFormatter());
        } catch (IllegalArgumentException e) {
            error.println(e.getMessage());
            return 2;
        }
        return 0;
    }

    static int formatWorkspace(
            final Path workspace,
            final List<String> rootNames,
            final List<String> fileNames,
            final PalantirFormatter formatter)
            throws IOException, FormatterException {
        final List<Path> roots = WorkspaceRoots.resolve(workspace, rootNames);
        final Set<Path> sources = new LinkedHashSet<>(WorkspaceRoots.discoverJavaSources(roots));
        final Path absoluteWorkspace = workspace.toAbsolutePath().normalize();
        for (final String fileName : fileNames) {
            final Path file = Path.of(fileName);
            final Path source = (file.isAbsolute() ? file : absoluteWorkspace.resolve(file)).normalize();
            if (null == source.getFileName()
                    || !source.getFileName().toString().endsWith(".java")
                    || !Files.isRegularFile(source)) {
                throw new IllegalArgumentException("Rejected Java format file: " + fileName);
            }
            sources.add(source);
        }
        int changed = 0;
        for (final Path source :
                sources.stream().sorted(Comparator.comparing(Path::toString)).toList()) {
            if (formatter.formatFile(source)) {
                changed++;
            }
        }
        return changed;
    }

    private static Arguments parse(final String[] args) {
        final List<String> rootNames = new ArrayList<>();
        final List<String> fileNames = new ArrayList<>();
        for (final String argument : args) {
            if (argument.startsWith("--root=") && argument.length() > "--root=".length()) {
                rootNames.add(argument.substring("--root=".length()));
            } else if (argument.isEmpty() || argument.startsWith("--")) {
                throw new IllegalArgumentException("usage: java_format [--root=PATH ...] [FILE ...]");
            } else {
                fileNames.add(argument);
            }
        }
        if (rootNames.isEmpty() && fileNames.isEmpty()) {
            throw new IllegalArgumentException("usage: java_format [--root=PATH ...] [FILE ...]");
        }
        return new Arguments(rootNames, fileNames);
    }

    private record Arguments(List<String> rootNames, List<String> fileNames) {}
}
