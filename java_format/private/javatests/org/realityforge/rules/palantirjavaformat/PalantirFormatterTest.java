package org.realityforge.rules.palantirjavaformat;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.javaformat.java.FormatterException;
import com.palantir.javaformat.java.Main;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PalantirFormatterTest {
    private final PalantirFormatter formatter = new PalantirFormatter();

    @Test
    void formatsSource() throws FormatterException {
        assertThat(formatter.format("package x; class X{void x(){System.out.println(\"x\");}}\n"))
                .isEqualTo("""
                    package x;

                    class X {
                        void x() {
                            System.out.println("x");
                        }
                    }
                    """);
    }

    @Test
    void matchesPalantirCliAndGoldenOutputs(@TempDir final Path tempDir) throws Exception {
        assertMatchesCli(
                tempDir.resolve("Imports.java"),
                "package x; import java.util.Set; import java.util.List; class X{List<String> x=List.of();}\n",
                """
                package x;

                import java.util.List;

                class X {
                    List<String> x = List.of();
                }
                """);
        assertMatchesCli(
                tempDir.resolve("LongString.java"),
                "package x; class X{String x=\"This is a deliberately long string that should be"
                        + " reflowed by the Palantir formatter because it extends well beyond the normal"
                        + " line width used for Java source code.\";}\n",
                """
                package x;

                class X {
                    String x = "This is a deliberately long string that should be reflowed by the Palantir formatter because it extends"
                            + " well beyond the normal line width used for Java source code.";
                }
                """);
        assertMatchesCli(
                tempDir.resolve("Javadoc.java"),
                "package x; /** this is documentation that should be formatted consistently by"
                        + " palantir java format. */ class X{}\n",
                """
                package x;
                /** this is documentation that should be formatted consistently by palantir java format. */
                class X {}
                """);
    }

    @Test
    void writesOnlyWhenContentDiffers(@TempDir final Path tempDir) throws IOException, FormatterException {
        final Path source = tempDir.resolve("X.java");
        Files.writeString(source, "class X{}\n", StandardCharsets.UTF_8);

        assertThat(formatter.formatFile(source)).isTrue();
        assertThat(Files.readString(source, StandardCharsets.UTF_8)).isEqualTo("class X {}\n");
        assertThat(formatter.formatFile(source)).isFalse();
    }

    private void assertMatchesCli(final Path path, final String source, final String expected) throws Exception {
        Files.writeString(path, source, StandardCharsets.UTF_8);
        final var output = new StringWriter();
        final var error = new StringWriter();
        final int exitCode = new Main(new PrintWriter(output), new PrintWriter(error), InputStream.nullInputStream())
                .format("--palantir", "--replace", path.toString());

        assertThat(exitCode).isZero();
        assertThat(output.toString()).isEmpty();
        assertThat(error.toString()).isEmpty();
        assertThat(formatter.format(source))
                .isEqualTo(Files.readString(path, StandardCharsets.UTF_8))
                .isEqualTo(expected);
    }
}
