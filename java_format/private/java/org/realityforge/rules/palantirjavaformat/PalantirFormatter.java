package org.realityforge.rules.palantirjavaformat;

import com.palantir.javaformat.java.FormatterException;
import com.palantir.javaformat.java.FormatterService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceLoader;

public final class PalantirFormatter {
    private final FormatterService formatter;

    public PalantirFormatter() {
        formatter = ServiceLoader.load(FormatterService.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Palantir formatter service is unavailable"));
    }

    public String format(final String source) throws FormatterException {
        return formatter.formatSourceReflowStringsAndFixImports(source);
    }

    public boolean formatFile(final Path path) throws IOException, FormatterException {
        final String source = Files.readString(path, StandardCharsets.UTF_8);
        final String formatted = format(source);
        if (source.equals(formatted)) {
            return false;
        }
        Files.writeString(path, formatted, StandardCharsets.UTF_8);
        return true;
    }
}
