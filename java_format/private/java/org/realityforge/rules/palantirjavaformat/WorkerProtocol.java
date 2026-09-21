package org.realityforge.rules.palantirjavaformat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class WorkerProtocol {
    private static final int MAXIMUM_MESSAGE_SIZE = 16 * 1024 * 1024;

    private WorkerProtocol() {}

    static @Nullable WorkRequest readRequest(final InputStream input) throws IOException {
        final @Nullable Long encodedLength = readOptionalVarint(input);
        if (null == encodedLength) {
            return null;
        }
        final var message = readMessage(input, encodedLength, "request");
        final List<String> arguments = new ArrayList<>();
        int requestId = 0;
        boolean cancel = false;
        while (0 != message.available()) {
            final long tag = readVarint(message);
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (1 == field && 2 == wireType) {
                arguments.add(new String(readBytes(message), StandardCharsets.UTF_8));
            } else if (3 == field && 0 == wireType) {
                requestId = (int) readVarint(message);
            } else if (4 == field && 0 == wireType) {
                cancel = 0 != readVarint(message);
            } else {
                skipField(message, wireType);
            }
        }
        return new WorkRequest(List.copyOf(arguments), requestId, cancel);
    }

    static void writeRequest(final WorkRequest request, final OutputStream output) throws IOException {
        final var message = new ByteArrayOutputStream();
        for (final String argument : request.arguments()) {
            writeStringField(message, 1, argument);
        }
        writeIntField(message, 3, request.requestId());
        if (request.cancel()) {
            writeIntField(message, 4, 1);
        }
        writeDelimited(message, output);
    }

    static void writeResponse(final WorkResponse response, final OutputStream output) throws IOException {
        final var message = new ByteArrayOutputStream();
        writeIntField(message, 1, response.exitCode());
        if (!response.output().isEmpty()) {
            writeStringField(message, 2, response.output());
        }
        writeIntField(message, 3, response.requestId());
        if (response.wasCancelled()) {
            writeIntField(message, 4, 1);
        }
        writeDelimited(message, output);
    }

    static @Nullable WorkResponse readResponse(final InputStream input) throws IOException {
        final @Nullable Long encodedLength = readOptionalVarint(input);
        if (null == encodedLength) {
            return null;
        }
        final var message = readMessage(input, encodedLength, "response");
        int exitCode = 0;
        String responseOutput = "";
        int requestId = 0;
        boolean wasCancelled = false;
        while (0 != message.available()) {
            final long tag = readVarint(message);
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (1 == field && 0 == wireType) {
                exitCode = (int) readVarint(message);
            } else if (2 == field && 2 == wireType) {
                responseOutput = new String(readBytes(message), StandardCharsets.UTF_8);
            } else if (3 == field && 0 == wireType) {
                requestId = (int) readVarint(message);
            } else if (4 == field && 0 == wireType) {
                wasCancelled = 0 != readVarint(message);
            } else {
                skipField(message, wireType);
            }
        }
        return new WorkResponse(exitCode, responseOutput, requestId, wasCancelled);
    }

    private static ByteArrayInputStream readMessage(
            final InputStream input, final long encodedLength, final String messageType) throws IOException {
        if (encodedLength < 0 || encodedLength > MAXIMUM_MESSAGE_SIZE) {
            throw new IOException("Invalid worker " + messageType + " size: " + encodedLength);
        }
        final byte[] bytes = input.readNBytes((int) encodedLength);
        if (bytes.length != encodedLength) {
            throw new EOFException("Truncated worker " + messageType);
        }
        return new ByteArrayInputStream(bytes);
    }

    private static byte[] readBytes(final InputStream input) throws IOException {
        final long encodedLength = readVarint(input);
        if (encodedLength < 0 || encodedLength > MAXIMUM_MESSAGE_SIZE) {
            throw new IOException("Invalid worker field size: " + encodedLength);
        }
        final byte[] bytes = input.readNBytes((int) encodedLength);
        if (bytes.length != encodedLength) {
            throw new EOFException("Truncated worker field");
        }
        return bytes;
    }

    private static void skipField(final InputStream input, final int wireType) throws IOException {
        if (0 == wireType) {
            readVarint(input);
            return;
        }
        final long length =
                switch (wireType) {
                    case 1 -> 8;
                    case 2 -> readVarint(input);
                    case 5 -> 4;
                    default -> throw new IOException("Unsupported worker protocol wire type: " + wireType);
                };
        if (length < 0 || length > MAXIMUM_MESSAGE_SIZE || input.readNBytes((int) length).length != length) {
            throw new EOFException("Truncated worker field");
        }
    }

    private static void writeStringField(final OutputStream output, final int field, final String value)
            throws IOException {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarint(output, (field << 3) | 2);
        writeVarint(output, bytes.length);
        output.write(bytes);
    }

    private static void writeIntField(final OutputStream output, final int field, final int value) throws IOException {
        if (0 != value) {
            writeVarint(output, field << 3);
            writeVarint(output, value);
        }
    }

    private static void writeDelimited(final ByteArrayOutputStream message, final OutputStream output)
            throws IOException {
        writeVarint(output, message.size());
        message.writeTo(output);
    }

    private static @Nullable Long readOptionalVarint(final InputStream input) throws IOException {
        final int first = input.read();
        return -1 == first ? null : readVarint(input, first);
    }

    private static long readVarint(final InputStream input) throws IOException {
        final int first = input.read();
        if (-1 == first) {
            throw new EOFException("Truncated worker protocol varint");
        }
        return readVarint(input, first);
    }

    private static long readVarint(final InputStream input, final int first) throws IOException {
        long value = first & 0x7FL;
        if (0 == (first & 0x80)) {
            return value;
        }
        for (int shift = 7; shift < 64; shift += 7) {
            final int next = input.read();
            if (-1 == next) {
                throw new EOFException("Truncated worker protocol varint");
            }
            value |= (long) (next & 0x7F) << shift;
            if (0 == (next & 0x80)) {
                return value;
            }
        }
        throw new IOException("Worker protocol varint is too long");
    }

    private static void writeVarint(final OutputStream output, final long value) throws IOException {
        long remaining = value;
        while (0 != (remaining & ~0x7FL)) {
            output.write((int) (remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        output.write((int) remaining);
    }

    record WorkRequest(List<String> arguments, int requestId, boolean cancel) {}

    record WorkResponse(int exitCode, String output, int requestId, boolean wasCancelled) {}
}
