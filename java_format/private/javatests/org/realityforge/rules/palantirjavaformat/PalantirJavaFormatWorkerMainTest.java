package org.realityforge.rules.palantirjavaformat;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.devtools.build.lib.worker.WorkerProtocol.WorkRequest;
import com.google.devtools.build.lib.worker.WorkerProtocol.WorkResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PalantirJavaFormatWorkerMainTest {
    @Test
    void processesRequestsPreservesIdsAndStopsAtEof(@TempDir final Path tempDir) throws IOException {
        final Path clean = write(tempDir.resolve("Clean.java"), "class Clean {}\n");
        final Path dirty = write(tempDir.resolve("Dirty.java"), "class Dirty{}\n");
        final Path cleanMarker = tempDir.resolve("clean.marker");
        final Path dirtyMarker = write(tempDir.resolve("dirty.marker"), "stale");
        final Path reportMarker = tempDir.resolve("report.marker");
        final var requests = new ByteArrayOutputStream();
        scanRequest(11, cleanMarker, clean).writeDelimitedTo(requests);
        scanRequest(12, dirtyMarker, dirty).writeDelimitedTo(requests);
        reportRequest(13, reportMarker, cleanMarker, dirtyMarker).writeDelimitedTo(requests);
        WorkRequest.newBuilder().setRequestId(14).setCancel(true).build().writeDelimitedTo(requests);
        final var responses = new ByteArrayOutputStream();

        PalantirJavaFormatWorkerMain.runPersistent(
                new PalantirFormatter(), new ByteArrayInputStream(requests.toByteArray()), responses);

        final var responseInput = new ByteArrayInputStream(responses.toByteArray());
        final WorkResponse cleanResponse = WorkResponse.parseDelimitedFrom(responseInput);
        final WorkResponse dirtyResponse = WorkResponse.parseDelimitedFrom(responseInput);
        final WorkResponse reportResponse = WorkResponse.parseDelimitedFrom(responseInput);
        final WorkResponse cancelResponse = WorkResponse.parseDelimitedFrom(responseInput);
        assertThat(cleanResponse.getRequestId()).isEqualTo(11);
        assertThat(cleanResponse.getExitCode()).isZero();
        assertThat(cleanResponse.getOutput()).isEmpty();
        assertThat(cleanMarker).exists();
        assertThat(dirtyResponse.getRequestId()).isEqualTo(12);
        assertThat(dirtyResponse.getExitCode()).isZero();
        assertThat(dirtyResponse.getOutput()).isEmpty();
        assertThat(dirtyMarker).content(StandardCharsets.UTF_8).isEqualTo(dirty + "\n");
        assertThat(dirty).content(StandardCharsets.UTF_8).isEqualTo("class Dirty{}\n");
        assertThat(reportResponse.getRequestId()).isEqualTo(13);
        assertThat(reportResponse.getExitCode()).isEqualTo(1);
        assertThat(reportResponse.getOutput())
                .contains(dirty.toString(), "Run ./tools/format.sh write")
                .doesNotContain(clean.toString());
        assertThat(reportMarker).doesNotExist();
        assertThat(cancelResponse.getRequestId()).isEqualTo(14);
        assertThat(cancelResponse.getWasCancelled()).isTrue();
        assertThat(responseInput.read()).isEqualTo(-1);
    }

    @Test
    void expandsParameterFileForOrdinaryExecution(@TempDir final Path tempDir) throws IOException {
        final Path clean = write(tempDir.resolve("Clean.java"), "class Clean {}\n");
        final Path marker = tempDir.resolve("clean.marker");
        final Path arguments = tempDir.resolve("worker.params");
        Files.write(arguments, List.of("--mode=scan", "--marker=" + marker, clean.toString()), StandardCharsets.UTF_8);
        final var errorBytes = new ByteArrayOutputStream();

        final int exitCode = PalantirJavaFormatWorkerMain.runOnce(
                new PalantirFormatter(),
                PalantirJavaFormatWorkerMain.expandArguments(new String[] {"@" + arguments}),
                new PrintStream(errorBytes, true, StandardCharsets.UTF_8));

        assertThat(exitCode).isZero();
        assertThat(errorBytes.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(marker).exists();
    }

    @Test
    void reportsDirtyScanMarkersInOrdinaryExecution(@TempDir final Path tempDir) throws IOException {
        final Path scanMarker = write(tempDir.resolve("scan.marker"), "src/Dirty.java\n");
        final Path reportMarker = tempDir.resolve("report.marker");
        final var errorBytes = new ByteArrayOutputStream();

        final int exitCode = PalantirJavaFormatWorkerMain.runOnce(
                new PalantirFormatter(),
                List.of(
                        "--mode=report",
                        "--marker=" + reportMarker,
                        "--remediation=./tools/format.sh write",
                        scanMarker.toString()),
                new PrintStream(errorBytes, true, StandardCharsets.UTF_8));

        assertThat(exitCode).isEqualTo(1);
        assertThat(errorBytes.toString(StandardCharsets.UTF_8))
                .contains("src/Dirty.java", "Run ./tools/format.sh write");
        assertThat(reportMarker).doesNotExist();
    }

    @Test
    void reportsInvalidRequestsWithoutWritingMarker(@TempDir final Path tempDir) {
        final Path marker = tempDir.resolve("invalid.marker");
        final var errorBytes = new ByteArrayOutputStream();

        final int exitCode = PalantirJavaFormatWorkerMain.runOnce(
                new PalantirFormatter(),
                List.of("--mode=write", "--marker=" + marker),
                new PrintStream(errorBytes, true, StandardCharsets.UTF_8));

        assertThat(exitCode).isEqualTo(1);
        assertThat(errorBytes.toString(StandardCharsets.UTF_8)).contains("--mode=scan", "--mode=report");
        assertThat(marker).doesNotExist();
    }

    private static WorkRequest scanRequest(final int requestId, final Path marker, final Path source) {
        return WorkRequest.newBuilder()
                .setRequestId(requestId)
                .addArguments("--mode=scan")
                .addArguments("--marker=" + marker)
                .addArguments(source.toString())
                .build();
    }

    private static WorkRequest reportRequest(
            final int requestId, final Path marker, final Path cleanMarker, final Path dirtyMarker) {
        return WorkRequest.newBuilder()
                .setRequestId(requestId)
                .addArguments("--mode=report")
                .addArguments("--marker=" + marker)
                .addArguments("--remediation=./tools/format.sh write")
                .addArguments(cleanMarker.toString())
                .addArguments(dirtyMarker.toString())
                .build();
    }

    private static Path write(final Path path, final String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }
}
