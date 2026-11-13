package com.tablebite.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * Reads newline-delimited messages from a socket stream with a hard size limit.
 *
 * <p>Unlike {@code BufferedReader.readLine()}, memory use is bounded: a line is only
 * buffered up to {@code maxLineBytes}. Bytes beyond that are counted and discarded
 * until the next newline, so an oversized message is rejected while it is being read.
 * If a client keeps sending without ever sending a newline, the reader gives up after
 * {@code maxDiscardBytes} and reports {@link Status#TOO_LARGE_FATAL}.
 *
 * <p>{@link #next()} performs at most one blocking read. When the socket read times out
 * (SO_TIMEOUT) or only part of a line has arrived, it returns {@link Status#PENDING}
 * and keeps the partial line, so the caller can check idle deadlines and shutdown
 * requests between reads without losing data.
 *
 * <p>Not thread-safe; owned by a single {@link ClientHandler}.
 */
final class TcpLineReader {

    enum Status {
        /** A complete line is available in {@link Result#text()}. */
        LINE,
        /** No complete line yet (read timed out or partial data). */
        PENDING,
        /** A complete line was received but exceeded the limit; it was discarded. */
        TOO_LARGE,
        /** The client sent too much data without a newline; the connection should close. */
        TOO_LARGE_FATAL,
        /** The client closed its side of the connection. */
        END_OF_STREAM
    }

    record Result(Status status, String text, long size) {
        static final Result PENDING = new Result(Status.PENDING, null, 0);
        static final Result END_OF_STREAM = new Result(Status.END_OF_STREAM, null, 0);
    }

    private final InputStream in;
    private final int maxLineBytes;
    private final long maxDiscardBytes;
    private final byte[] buffer = new byte[8192];
    private final ByteArrayOutputStream line;
    private int position;
    private int limit;
    private boolean discarding;
    private long discarded;
    /** True once any byte of the current (incomplete) message has been received. */
    private boolean partial;

    TcpLineReader(InputStream in, int maxLineBytes, long maxDiscardBytes) {
        if (maxLineBytes <= 0 || maxDiscardBytes < maxLineBytes) {
            throw new IllegalArgumentException("Invalid line limits");
        }
        this.in = in;
        this.maxLineBytes = maxLineBytes;
        this.maxDiscardBytes = maxDiscardBytes;
        this.line = new ByteArrayOutputStream(Math.min(maxLineBytes, 1024));
    }

    /** True when part of a message has been received but not its terminating newline. */
    boolean hasPartialMessage() {
        return partial;
    }

    Result next() throws IOException {
        Result buffered = scan();
        if (buffered != null) {
            return buffered;
        }
        int read;
        try {
            read = in.read(buffer);
        } catch (SocketTimeoutException timeout) {
            return Result.PENDING;
        }
        if (read < 0) {
            return Result.END_OF_STREAM;
        }
        position = 0;
        limit = read;
        Result scanned = scan();
        return scanned != null ? scanned : Result.PENDING;
    }

    /** Consumes buffered bytes; returns a result once a line ends, else null. */
    private Result scan() {
        while (position < limit) {
            byte value = buffer[position++];
            partial = true;
            if (value == '\n') {
                return finishLine();
            }
            if (discarding) {
                if (++discarded > maxDiscardBytes) {
                    return new Result(Status.TOO_LARGE_FATAL, null, discarded);
                }
            } else if (line.size() >= maxLineBytes) {
                // Stop buffering: drop what we have and count the rest.
                discarding = true;
                discarded = line.size() + 1L;
                line.reset();
            } else {
                line.write(value);
            }
        }
        return null;
    }

    private Result finishLine() {
        partial = false;
        if (discarding) {
            long size = discarded;
            discarding = false;
            discarded = 0;
            return new Result(Status.TOO_LARGE, null, size);
        }
        byte[] bytes = line.toByteArray();
        line.reset();
        int length = bytes.length;
        if (length > 0 && bytes[length - 1] == '\r') {
            length--;
        }
        return new Result(Status.LINE, new String(bytes, 0, length, StandardCharsets.UTF_8), length);
    }
}
