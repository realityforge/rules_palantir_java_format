package org.realityforge.rules.palantirjavaformat;

import com.palantir.javaformat.java.FormatterException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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
        final List<String> rootNames;
        try {
            rootNames = WorkspaceRoots.parse(args);
            formatWorkspace(Path.of(workspace), rootNames, new PalantirFormatter());
        } catch (IllegalArgumentException e) {
            error.println(e.getMessage());
            return 2;
        }
        return 0;
    }

    static int formatWorkspace(final Path workspace, final List<String> rootNames, final PalantirFormatter formatter)
            throws IOException, FormatterException {
        int changed = 0;
        final List<Path> roots = WorkspaceRoots.resolve(workspace, rootNames);
        for (final Path source : WorkspaceRoots.discoverJavaSources(roots)) {
            if (formatter.formatFile(source)) {
                changed++;
            }
        }
        return changed;
    }
}
