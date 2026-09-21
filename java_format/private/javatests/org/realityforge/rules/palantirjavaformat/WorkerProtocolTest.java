package org.realityforge.rules.palantirjavaformat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

final class WorkerProtocolTest {
    @Test
    void decodesCanonicalRawRequestGoldenVector() throws IOException {
        final WorkerProtocol.WorkRequest request = Objects.requireNonNull(WorkerProtocol.readRequest(input(
                0x11, 0x0A, 0x0B, '-', '-', 'm', 'o', 'd', 'e', '=', 's', 'c', 'a', 'n', 0x18, 0x07, 0x20, 0x01)));

        assertThat(request.arguments()).isEqualTo(List.of("--mode=scan"));
        assertThat(request.requestId()).isEqualTo(7);
        assertThat(request.cancel()).isTrue();
    }

    @Test
    void decodesCanonicalRawResponseGoldenVector() throws IOException {
        final WorkerProtocol.WorkResponse response = Objects.requireNonNull(WorkerProtocol.readResponse(
                input(0x0C, 0x08, 0x01, 0x12, 0x04, 'b', 'a', 'd', '\n', 0x18, 0x07, 0x20, 0x01)));

        assertThat(response.exitCode()).isEqualTo(1);
        assertThat(response.output()).isEqualTo("bad\n");
        assertThat(response.requestId()).isEqualTo(7);
        assertThat(response.wasCancelled()).isTrue();
    }

    @Test
    void skipsRawUnknownFieldsForEverySupportedWireType() throws IOException {
        final WorkerProtocol.WorkRequest request = Objects.requireNonNull(WorkerProtocol.readRequest(input(
                0x16, 0x38, 0x63, 0x41, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x4A, 0x02, 0xAA, 0xBB, 0x55,
                0x09, 0x0A, 0x0B, 0x0C, 0x18, 0x09)));

        assertThat(request.arguments()).isEmpty();
        assertThat(request.requestId()).isEqualTo(9);
        assertThat(request.cancel()).isFalse();
    }

    @Test
    void rejectsTruncatedMessage() {
        assertThatIOException()
                .isThrownBy(() -> WorkerProtocol.readRequest(input(0x03, 0x18, 0x01)))
                .withMessage("Truncated worker request");
    }

    @Test
    void rejectsOversizedMessage() {
        assertThatIOException()
                .isThrownBy(() -> WorkerProtocol.readRequest(input(0x81, 0x80, 0x80, 0x08)))
                .withMessage("Invalid worker request size: 16777217");
    }

    @Test
    void rejectsMalformedLengthVarint() {
        assertThatIOException()
                .isThrownBy(() ->
                        WorkerProtocol.readRequest(input(0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80)))
                .withMessage("Worker protocol varint is too long");
    }

    private static ByteArrayInputStream input(final int... values) {
        final byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return new ByteArrayInputStream(bytes);
    }
}
