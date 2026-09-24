package org.realityforge.rules.palantirjavaformat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.palantir.javaformat.java.FormatterException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PalantirJavaFormatMainTest {
    @Test
    void formatsOnlyExplicitSafeRootsInDeterministicOrder(@TempDir final Path tempDir)
            throws IOException, FormatterException {
        final Path first = write(tempDir.resolve("alpha/X.java"), "class X{}\n");
        final Path second = write(tempDir.resolve("beta/Y.java"), "class Y{}\n");
        final Path ignored = write(tempDir.resolve("other/Z.java"), "class Z{}\n");
        final Path text = write(tempDir.resolve("alpha/readme.txt"), "class Readme{}\n");
        final Path linkedTarget = write(tempDir.resolve("Linked.java"), "class Linked{}\n");
        createSymbolicLinkIfSupported(tempDir.resolve("alpha/Linked.java"), linkedTarget);

        assertThat(WorkspaceRoots.discoverJavaSources(
                        WorkspaceRoots.resolve(tempDir, List.of("beta", "alpha", "alpha"))))
                .containsExactly(first, second);
        assertThat(PalantirJavaFormatMain.formatWorkspace(
                        tempDir, List.of("beta", "alpha", "alpha"), List.of(), new PalantirFormatter()))
                .isEqualTo(2);
        assertThat(first).content(StandardCharsets.UTF_8).isEqualTo("class X {}\n");
        assertThat(second).content(StandardCharsets.UTF_8).isEqualTo("class Y {}\n");
        assertThat(ignored).content(StandardCharsets.UTF_8).isEqualTo("class Z{}\n");
        assertThat(text).content(StandardCharsets.UTF_8).isEqualTo("class Readme{}\n");
        assertThat(linkedTarget).content(StandardCharsets.UTF_8).isEqualTo("class Linked{}\n");
    }

    @Test
    void formatsMixedRootsAndExplicitFilesIncludingOutsideWorkspace(@TempDir final Path tempDir)
            throws IOException, FormatterException {
        final Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        final Path inRoot = write(workspace.resolve("src/InRoot.java"), "class InRoot{}\n");
        final Path relative = write(workspace.resolve("other/Relative.java"), "class Relative{}\n");
        final Path outside = write(tempDir.resolve("Outside.java"), "class Outside{}\n");
        final Path linkedTarget = write(tempDir.resolve("LinkedTarget.java"), "class LinkedTarget{}\n");
        final Path untouched = write(workspace.resolve("other/Untouched.java"), "class Untouched{}\n");
        final Path link = workspace.resolve("other/Linked.java");
        final boolean symlinkCreated = createSymbolicLinkIfSupported(link, linkedTarget);
        final List<String> files = symlinkCreated
                ? List.of("other/Relative.java", outside.toString(), "other/Linked.java", inRoot.toString())
                : List.of("other/Relative.java", outside.toString(), inRoot.toString());

        assertThat(PalantirJavaFormatMain.formatWorkspace(workspace, List.of("src"), files, new PalantirFormatter()))
                .isEqualTo(symlinkCreated ? 4 : 3);
        assertThat(inRoot).content(StandardCharsets.UTF_8).isEqualTo("class InRoot {}\n");
        assertThat(relative).content(StandardCharsets.UTF_8).isEqualTo("class Relative {}\n");
        assertThat(outside).content(StandardCharsets.UTF_8).isEqualTo("class Outside {}\n");
        assertThat(linkedTarget)
                .content(StandardCharsets.UTF_8)
                .isEqualTo(symlinkCreated ? "class LinkedTarget {}\n" : "class LinkedTarget{}\n");
        assertThat(untouched).content(StandardCharsets.UTF_8).isEqualTo("class Untouched{}\n");
    }

    @Test
    void rejectsInvalidFilesBeforeWritingAnySource(@TempDir final Path tempDir) throws IOException {
        final Path valid = write(tempDir.resolve("src/Valid.java"), "class Valid{}\n");
        final Path text = write(tempDir.resolve("NotJava.txt"), "class NotJava{}\n");
        for (final String invalid : List.of("Missing.java", text.toString(), "src")) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> PalantirJavaFormatMain.formatWorkspace(
                            tempDir, List.of("src"), List.of(valid.toString(), invalid), new PalantirFormatter()))
                    .withMessageContaining("Rejected Java format file: " + invalid);
            assertThat(valid).content(StandardCharsets.UTF_8).isEqualTo("class Valid{}\n");
        }
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PalantirJavaFormatMain.formatWorkspace(
                        tempDir, List.of("missing"), List.of(valid.toString()), new PalantirFormatter()));
        assertThat(valid).content(StandardCharsets.UTF_8).isEqualTo("class Valid{}\n");
    }

    @Test
    void rejectsUnsafeRoots(@TempDir final Path tempDir) throws IOException {
        final Path source = Files.createDirectories(tempDir.resolve("src"));
        final Path outside = Files.createDirectories(tempDir.resolveSibling(tempDir.getFileName() + "-outside"));
        final Path linked = tempDir.resolve("linked");
        final boolean symlinkCreated = createSymbolicLinkIfSupported(linked, source);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> WorkspaceRoots.resolve(tempDir, List.of(outside.toString())));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> WorkspaceRoots.resolve(tempDir, List.of("../" + outside.getFileName())));
        assertThatIllegalArgumentException().isThrownBy(() -> WorkspaceRoots.resolve(tempDir, List.of("missing")));
        if (symlinkCreated) {
            assertThatIllegalArgumentException().isThrownBy(() -> WorkspaceRoots.resolve(tempDir, List.of("linked")));
        }

        Files.deleteIfExists(linked);
        Files.delete(outside);
    }

    @Test
    void validatesInvocation(@TempDir final Path tempDir) throws IOException, FormatterException {
        Files.createDirectories(tempDir.resolve("src"));
        final var errorBytes = new ByteArrayOutputStream();
        final var error = new PrintStream(errorBytes, true, StandardCharsets.UTF_8);

        assertThat(PalantirJavaFormatMain.run(new String[0], Map.of(), error)).isEqualTo(2);
        assertThat(errorBytes.toString(StandardCharsets.UTF_8)).contains("BUILD_WORKSPACE_DIRECTORY is not set");

        errorBytes.reset();
        assertThat(PalantirJavaFormatMain.run(
                        new String[0], Map.of("BUILD_WORKSPACE_DIRECTORY", tempDir.toString()), error))
                .isEqualTo(2);
        assertThat(errorBytes.toString(StandardCharsets.UTF_8)).contains("usage: java_format");

        errorBytes.reset();
        assertThat(PalantirJavaFormatMain.run(
                        new String[] {"--root=missing"},
                        Map.of("BUILD_WORKSPACE_DIRECTORY", tempDir.toString()),
                        error))
                .isEqualTo(2);
        assertThat(errorBytes.toString(StandardCharsets.UTF_8)).contains("Rejected Java format root: missing");

        assertThat(PalantirJavaFormatMain.run(
                        new String[] {"--root=src"}, Map.of("BUILD_WORKSPACE_DIRECTORY", tempDir.toString()), error))
                .isZero();

        final Path source = write(tempDir.resolve("File.java"), "class File{}\n");
        assertThat(PalantirJavaFormatMain.run(
                        new String[] {source.toString()},
                        Map.of("BUILD_WORKSPACE_DIRECTORY", tempDir.toString()),
                        error))
                .isZero();
        assertThat(source).content(StandardCharsets.UTF_8).isEqualTo("class File {}\n");
    }

    private static Path write(final Path path, final String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private static boolean createSymbolicLinkIfSupported(final Path link, final Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (IOException | UnsupportedOperationException | SecurityException ignored) {
            return false;
        }
    }
}
