package org.realityforge.rules.palantirjavaformat;

import com.palantir.javaformat.java.FormatterException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

public final class PalantirJavaFormatWorkerMain {
    private static final String PERSISTENT_WORKER_ARGUMENT = "--persistent_worker";

    private PalantirJavaFormatWorkerMain() {}

    public static void main(final String[] args) throws IOException {
        final var formatter = new PalantirFormatter();
        if (Arrays.asList(args).contains(PERSISTENT_WORKER_ARGUMENT)) {
            runPersistent(formatter, System.in, System.out);
        } else {
            System.exit(runOnce(formatter, expandArguments(args), System.err));
        }
    }

    static void runPersistent(final PalantirFormatter formatter, final InputStream input, final OutputStream output)
            throws IOException {
        while (true) {
            final var request = WorkerProtocol.readRequest(input);
            if (null == request) {
                return;
            }
            final WorkerProtocol.WorkResponse response;
            if (request.cancel()) {
                response = new WorkerProtocol.WorkResponse(0, "", request.requestId(), true);
            } else {
                final CheckResult result = process(formatter, request.arguments());
                response =
                        new WorkerProtocol.WorkResponse(result.exitCode(), result.output(), request.requestId(), false);
            }
            WorkerProtocol.writeResponse(response, output);
            output.flush();
        }
    }

    static int runOnce(final PalantirFormatter formatter, final List<String> arguments, final PrintStream error) {
        final CheckResult result = process(formatter, arguments);
        if (!result.output().isEmpty()) {
            error.print(result.output());
        }
        return result.exitCode();
    }

    static List<String> expandArguments(final String[] args) throws IOException {
        final List<String> arguments = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            final String argument = args[i];
            if (i == args.length - 1 && argument.startsWith("@")) {
                arguments.addAll(Files.readAllLines(Path.of(argument.substring(1)), StandardCharsets.UTF_8));
            } else {
                arguments.add(argument);
            }
        }
        return List.copyOf(arguments);
    }

    private static CheckResult process(final PalantirFormatter formatter, final List<String> arguments) {
        try {
            final CheckRequest request = CheckRequest.parse(arguments);
            return switch (request.mode()) {
                case "scan" -> scan(formatter, request);
                case "report" -> report(request);
                default -> throw new IllegalArgumentException("Unknown worker mode: " + request.mode());
            };
        } catch (IOException | FormatterException | IllegalArgumentException e) {
            return new CheckResult(1, errorMessage(e) + "\n");
        }
    }

    private static CheckResult scan(final PalantirFormatter formatter, final CheckRequest request)
            throws IOException, FormatterException {
        Files.deleteIfExists(request.marker());
        final List<String> dirty = new ArrayList<>();
        for (final Source source : request.sources()) {
            final String content = Files.readString(source.path(), StandardCharsets.UTF_8);
            if (!content.equals(formatter.format(content))) {
                dirty.add(source.displayPath());
            }
        }
        writeMarker(request.marker(), dirty.isEmpty() ? "" : String.join("\n", dirty) + "\n");
        return new CheckResult(0, "");
    }

    private static CheckResult report(final CheckRequest request) throws IOException {
        Files.deleteIfExists(request.marker());
        final Set<String> dirty = new TreeSet<>();
        for (final Source source : request.sources()) {
            dirty.addAll(Files.readAllLines(source.path(), StandardCharsets.UTF_8));
        }
        dirty.remove("");
        if (!dirty.isEmpty()) {
            return new CheckResult(
                    1,
                    "Unformatted Java files:\n  "
                            + String.join("\n  ", dirty)
                            + "\nRun "
                            + request.remediation()
                            + "\n");
        }
        writeMarker(request.marker(), "");
        return new CheckResult(0, "");
    }

    private static void writeMarker(final Path marker, final String content) throws IOException {
        final @Nullable Path parent = marker.getParent();
        if (null != parent) {
            Files.createDirectories(parent);
        }
        Files.writeString(marker, content, StandardCharsets.UTF_8);
    }

    private static String errorMessage(final Exception exception) {
        final @Nullable String message = exception.getMessage();
        return null == message ? exception.getClass().getSimpleName() : message;
    }

    private record CheckResult(int exitCode, String output) {}

    private record CheckRequest(String mode, Path marker, String remediation, List<Source> sources) {
        private static CheckRequest parse(final List<String> arguments) {
            String mode = "";
            @Nullable Path marker = null;
            String remediation = "";
            final List<Source> sources = new ArrayList<>();
            for (final String argument : arguments) {
                if (argument.startsWith("--mode=")) {
                    mode = argument.substring("--mode=".length());
                } else if (argument.startsWith("--marker=")) {
                    marker = Path.of(argument.substring("--marker=".length()));
                } else if (argument.startsWith("--remediation=")) {
                    remediation = argument.substring("--remediation=".length());
                } else if (argument.startsWith("--")) {
                    throw new IllegalArgumentException("Unknown worker argument: " + argument);
                } else {
                    sources.add(new Source(Path.of(argument), argument));
                }
            }
            if (!"scan".equals(mode) && !"report".equals(mode)) {
                throw new IllegalArgumentException("Worker requires --mode=scan or --mode=report");
            }
            if (null == marker) {
                throw new IllegalArgumentException("Worker requires --marker=<path>");
            }
            if ("report".equals(mode) && remediation.isBlank()) {
                throw new IllegalArgumentException("Worker requires --remediation=<command>");
            }
            sources.sort(Comparator.comparing(Source::displayPath));
            return new CheckRequest(mode, marker, remediation, List.copyOf(sources));
        }
    }

    private record Source(Path path, String displayPath) {}
}
