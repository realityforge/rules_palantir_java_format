package org.realityforge.rules.palantirjavaformat;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class PalantirJavaFormatWatchMain {
    private PalantirJavaFormatWatchMain() {}

    public static void main(final String[] args) throws IOException, InterruptedException {
        System.exit(run(args, System.getenv(), System.out, System.err));
    }

    static int run(
            final String[] args,
            final Map<String, String> environment,
            final PrintStream output,
            final PrintStream error)
            throws IOException, InterruptedException {
        final @Nullable String workspace = environment.get("BUILD_WORKSPACE_DIRECTORY");
        if (null == workspace || workspace.isBlank()) {
            error.println("BUILD_WORKSPACE_DIRECTORY is not set");
            return 2;
        }
        final List<String> rootNames;
        try {
            rootNames = WorkspaceRoots.parse(args, "java_format_watch");
            try (var watcher = new PalantirJavaFormatWatcher(
                    Path.of(workspace), rootNames, new PalantirFormatter(), output, error)) {
                output.println("Watching Java sources under " + String.join(", ", rootNames));
                watcher.watch();
            }
        } catch (IllegalArgumentException e) {
            error.println(e.getMessage());
            return 2;
        }
        return 0;
    }
}
