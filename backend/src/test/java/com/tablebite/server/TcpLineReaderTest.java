package com.tablebite.server;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TcpLineReaderTest {

    private static TcpLineReader reader(String data, int max, long discard) {
        return new TcpLineReader(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)), max, discard);
    }

    /** Reads until something other than PENDING is returned. */
    private static TcpLineReader.Result next(TcpLineReader reader) throws IOException {
        TcpLineReader.Result result;
        do {
            result = reader.next();
        } while (result.status() == TcpLineReader.Status.PENDING);
        return result;
    }

    @Test
    void splitsLinesAndStripsCarriageReturns() throws IOException {
        TcpLineReader reader = reader("a\r\nb\n\nü\n", 16, 64);
        assertEquals("a", next(reader).text());
        assertEquals("b", next(reader).text());
        assertEquals("", next(reader).text());
        assertEquals("ü", next(reader).text());
        assertEquals(TcpLineReader.Status.END_OF_STREAM, next(reader).status());
    }

    @Test
    void oversizedLineIsDiscardedAndNextLineStillReadable() throws IOException {
        TcpLineReader reader = reader("x".repeat(20) + "\nok\n", 8, 64);
        TcpLineReader.Result tooLarge = next(reader);
        assertEquals(TcpLineReader.Status.TOO_LARGE, tooLarge.status());
        assertEquals(20, tooLarge.size());
        assertEquals("ok", next(reader).text());
    }

    @Test
    void lineExactlyAtLimitIsAccepted() throws IOException {
        TcpLineReader reader = reader("12345678\n123456789\n", 8, 64);
        assertEquals("12345678", next(reader).text());
        assertEquals(TcpLineReader.Status.TOO_LARGE, next(reader).status());
    }

    @Test
    void endlessLineWithoutNewlineIsFatal() throws IOException {
        TcpLineReader reader = reader("z".repeat(1_000), 8, 100);
        assertEquals(TcpLineReader.Status.TOO_LARGE_FATAL, next(reader).status());
    }

    @Test
    void partialLineSurvivesReadTimeouts() throws IOException {
        Deque<Object> script = new ArrayDeque<>();
        script.add("{\"ty");
        script.add(new SocketTimeoutException());
        script.add("pe\":\"PING\"}\n");
        TcpLineReader reader = new TcpLineReader(new ScriptedStream(script), 64, 128);

        assertEquals(TcpLineReader.Status.PENDING, reader.next().status());
        assertTrue(reader.hasPartialMessage());
        assertEquals(TcpLineReader.Status.PENDING, reader.next().status());   // timeout
        TcpLineReader.Result line = reader.next();
        assertEquals(TcpLineReader.Status.LINE, line.status());
        assertEquals("{\"type\":\"PING\"}", line.text());
        assertFalse(reader.hasPartialMessage());
    }

    /** Returns one scripted chunk (or throws one scripted exception) per read call. */
    private static final class ScriptedStream extends InputStream {
        private final Deque<Object> script;

        ScriptedStream(Deque<Object> script) {
            this.script = script;
        }

        @Override
        public int read() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            Object step = script.poll();
            if (step == null) {
                return -1;
            }
            if (step instanceof IOException exception) {
                throw exception;
            }
            byte[] bytes = ((String) step).getBytes(StandardCharsets.UTF_8);
            System.arraycopy(bytes, 0, buffer, offset, bytes.length);
            return bytes.length;
        }
    }
}
