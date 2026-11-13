package com.tablebite.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.tablebite.server.TcpTestSupport.Client;
import com.tablebite.server.TcpTestSupport.FakeMenu;
import com.tablebite.server.TcpTestSupport.FakeOrders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Connection-level behaviour of {@link TcpServer} over real loopback sockets. */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class TcpServerTest {
    private final FakeOrders orders = new FakeOrders();
    private final FakeMenu menu = new FakeMenu();
    private TcpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    private TcpServer start(TcpServer.Settings settings) throws IOException {
        server = TcpTestSupport.startServer(settings, TcpTestSupport.services(orders, menu));
        return server;
    }

    private TcpServer start() throws IOException {
        return start(TcpTestSupport.settings(8, 4, 10_000, 2_000));
    }

    @Test
    void bindsToLoopbackByDefault() throws Exception {
        assertEquals("127.0.0.1", TcpServer.Settings.defaults(5000).host());
        start();
        try (Socket probe = new Socket("127.0.0.1", server.getLocalPort())) {
            assertTrue(probe.getInetAddress().isLoopbackAddress());
        }
        InetAddress lan = firstNonLoopbackAddress();
        if (lan != null) {
            // Not reachable from other interfaces.
            assertThrows(IOException.class, () -> {
                try (Socket socket = new Socket()) {
                    socket.connect(new java.net.InetSocketAddress(lan, server.getLocalPort()), 1_000);
                }
            });
        }
    }

    @Test
    void pingReturnsPong() throws Exception {
        start();
        try (Client client = new Client(server.getLocalPort())) {
            JsonNode pong = client.request("{\"type\":\"PING\"}");
            assertEquals("PONG", pong.get("type").asText());
            assertEquals("TableBite server is running", pong.get("message").asText());
            // Several requests on one connection, CRLF line endings accepted.
            client.sendRaw("{\"type\":\"PING\"}\r\n");
            assertEquals("PONG", client.read().get("type").asText());
        }
    }

    @Test
    void servesManyClientsConcurrently() throws Exception {
        start(TcpTestSupport.settings(10, 10, 10_000, 2_000));
        int clients = 10;
        CountDownLatch allConnected = new CountDownLatch(clients);
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < clients; i++) {
                results.add(pool.submit((Callable<Integer>) () -> {
                    try (Client client = new Client(server.getLocalPort())) {
                        allConnected.countDown();
                        assertTrue(allConnected.await(5, TimeUnit.SECONDS));
                        int pongs = 0;
                        for (int j = 0; j < 20; j++) {
                            if ("PONG".equals(client.request("{\"type\":\"PING\"}").get("type").asText())) {
                                pongs++;
                            }
                        }
                        return pongs;
                    }
                }));
            }
            for (Future<Integer> result : results) {
                assertEquals(20, result.get(10, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        waitUntil(() -> server.activeConnectionCount() == 0);
    }

    @Test
    void malformedAndUnsupportedMessagesGetErrorsAndConnectionStaysUsable() throws Exception {
        start();
        try (Client client = new Client(server.getLocalPort())) {
            assertError(client.request("{not json"), "INVALID_JSON");
            assertError(client.request("[1,2,3]"), "VALIDATION_ERROR");
            assertError(client.request("{\"type\":42}"), "VALIDATION_ERROR");
            assertError(client.request("{\"kind\":\"PING\"}"), "VALIDATION_ERROR");
            assertError(client.request("{\"type\":\"DROP_TABLES\"}"), "UNKNOWN_MESSAGE_TYPE");
            assertError(client.request("ÿþ garbage"), "INVALID_JSON");
            assertError(client.request("[".repeat(5_000)), "INVALID_JSON");
            client.send("   ");   // blank lines are ignored, no response
            assertEquals("PONG", client.request("{\"type\":\"PING\"}").get("type").asText());
        }
    }

    @Test
    void oversizedMessageIsRejectedAndConnectionStaysUsable() throws Exception {
        start();
        try (Client client = new Client(server.getLocalPort())) {
            String payload = "{\"type\":\"PING\",\"message\":\"" + "x".repeat(200_000) + "\"}";
            JsonNode error = client.request(payload);
            assertError(error, "MESSAGE_TOO_LARGE");
            assertEquals("PONG", client.request("{\"type\":\"PING\"}").get("type").asText());
            // Exactly at the limit is still accepted (parsed, then rejected as invalid JSON).
            assertError(client.request("x".repeat(64 * 1024)), "INVALID_JSON");
            assertError(client.request("x".repeat(64 * 1024 + 1)), "MESSAGE_TOO_LARGE");
        }
    }

    @Test
    void unterminatedFloodIsCutOffWithoutAffectingOtherClients() throws Exception {
        start();
        try (Client flooder = new Client(server.getLocalPort());
             Client healthy = new Client(server.getLocalPort())) {
            byte[] chunk = "y".repeat(16 * 1024).getBytes();
            try {
                for (int i = 0; i < 40; i++) {    // 640 KB > 256 KB discard limit, no newline
                    flooder.socket.getOutputStream().write(chunk);
                }
            } catch (IOException expected) {
                // the server may already have closed the connection
            }
            assertEquals("PONG", healthy.request("{\"type\":\"PING\"}").get("type").asText());
            try {
                assertError(flooder.read(), "MESSAGE_TOO_LARGE");
            } catch (IOException reset) {
                // On some platforms unread data causes a reset before the error arrives.
            }
            assertTrue(flooder.isClosedByServer());
            assertEquals("PONG", healthy.request("{\"type\":\"PING\"}").get("type").asText());
        }
    }

    @Test
    void idleConnectionTimesOut() throws Exception {
        start(TcpTestSupport.settings(4, 4, 300, 2_000));
        try (Client client = new Client(server.getLocalPort())) {
            assertEquals("PONG", client.request("{\"type\":\"PING\"}").get("type").asText());
            long started = System.nanoTime();
            assertError(client.read(), "IDLE_TIMEOUT");
            assertTrue(client.isClosedByServer());
            long waited = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue(waited >= 250 && waited < 3_000, "timed out after " + waited + " ms");
        }
        waitUntil(() -> server.activeConnectionCount() == 0);
    }

    @Test
    void tricklingPartialMessageCannotAvoidIdleTimeout() throws Exception {
        start(TcpTestSupport.settings(4, 4, 400, 2_000));
        try (Client client = new Client(server.getLocalPort())) {
            long started = System.nanoTime();
            // One byte every 50 ms, never a newline, until the server answers or 3 s pass.
            while (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 3_000
                    && client.socket.getInputStream().available() == 0) {
                try {
                    client.sendRaw("{");
                } catch (IOException closedByServer) {
                    break;
                }
                Thread.sleep(50);
            }
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue(elapsed < 1_500, "trickling kept the connection open for " + elapsed + " ms");
            try {
                assertError(client.read(), "IDLE_TIMEOUT");
            } catch (IOException reset) {
                // Windows may discard the notice if our last byte raced the close (RST).
            }
            assertTrue(client.isClosedByServer());
        }
    }

    @Test
    void abruptDisconnectsAreCleanedUpAndOtherClientsUnaffected() throws Exception {
        start();
        try (Client survivor = new Client(server.getLocalPort())) {
            assertEquals("PONG", survivor.request("{\"type\":\"PING\"}").get("type").asText());

            Client halfMessage = new Client(server.getLocalPort());
            halfMessage.sendRaw("{\"type\":\"PI");   // disconnect mid-message
            halfMessage.close();

            Client reset = new Client(server.getLocalPort());
            reset.request("{\"type\":\"PING\"}");
            reset.socket.setSoLinger(true, 0);         // close with TCP RST
            reset.close();

            Client noRead = new Client(server.getLocalPort());
            noRead.send("{\"type\":\"PING\"}");       // leaves before reading the reply
            noRead.close();

            waitUntil(() -> server.activeConnectionCount() == 1);
            assertEquals("PONG", survivor.request("{\"type\":\"PING\"}").get("type").asText());
        }
        waitUntil(() -> server.activeConnectionCount() == 0);
    }

    @Test
    void overloadIsRejectedWithServerBusyInsteadOfQueuingForever() throws Exception {
        start(TcpTestSupport.settings(1, 1, 10_000, 2_000));
        try (Client first = new Client(server.getLocalPort())) {
            // Make sure the only worker is busy with the first connection.
            assertEquals("PONG", first.request("{\"type\":\"PING\"}").get("type").asText());
            try (Client queued = new Client(server.getLocalPort())) {
                waitUntil(() -> server.activeConnectionCount() == 2);
                queued.send("{\"type\":\"PING\"}");
                try (Client rejected = new Client(server.getLocalPort())) {
                    assertError(rejected.read(), "SERVER_BUSY");
                    assertTrue(rejected.isClosedByServer());
                }
                // The first client is unaffected; once it leaves, the queued client is served.
                assertEquals("PONG", first.request("{\"type\":\"PING\"}").get("type").asText());
                first.close();
                assertEquals("PONG", queued.read().get("type").asText());
            }
        }
    }

    @Test
    void gracefulShutdownNotifiesClientsAndRefusesNewConnections() throws Exception {
        start();
        int port = server.getLocalPort();
        try (Client a = new Client(port); Client b = new Client(port)) {
            a.request("{\"type\":\"PING\"}");
            b.request("{\"type\":\"PING\"}");
            long started = System.nanoTime();
            server.stop();
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 2_000);
            assertFalse(server.isRunning());
            assertEquals(0, server.activeConnectionCount());
            assertEquals("SERVER_SHUTDOWN", a.read().get("type").asText());
            assertEquals("SERVER_SHUTDOWN", b.read().get("type").asText());
            assertTrue(a.isClosedByServer());
            assertTrue(b.isClosedByServer());
        }
        assertThrows(ConnectException.class, () -> new Socket("127.0.0.1", port).close());
        server.stop();   // idempotent
    }

    @Test
    void shutdownLetsInFlightRequestFinish() throws Exception {
        start();
        menu.delayMillis = 400;
        try (Client client = new Client(server.getLocalPort())) {
            client.send("{\"type\":\"GET_MENU\"}");
            Thread.sleep(100);    // request is now executing
            server.stop();
            JsonNode menuResponse = client.read();
            assertTrue(menuResponse.isArray(), "in-flight response should be delivered: " + menuResponse);
            assertEquals("SERVER_SHUTDOWN", client.read().get("type").asText());
        }
    }

    @Test
    void shutdownForceClosesConnectionsStuckPastGracePeriod() throws Exception {
        start(TcpTestSupport.settings(4, 4, 10_000, 200));
        menu.delayMillis = 20_000;
        try (Client client = new Client(server.getLocalPort())) {
            client.send("{\"type\":\"GET_MENU\"}");
            Thread.sleep(100);
            long started = System.nanoTime();
            server.stop();
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 3_000);
            assertTrue(client.isClosedByServer());
            assertEquals(0, server.activeConnectionCount());
        }
    }

    @Test
    void startFailsFastWhenPortIsTaken() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            TcpServer.Settings defaults = TcpTestSupport.settings(2, 2, 1_000, 100);
            TcpServer.Settings taken = new TcpServer.Settings(defaults.host(), occupied.getLocalPort(),
                    2, 2, 1_000, 25, defaults.maxMessageBytes(), defaults.maxDiscardBytes(), 100);
            TcpServer conflicting = new TcpServer(taken, TcpTestSupport.services(orders, menu));
            assertThrows(IOException.class, conflicting::start);
            assertFalse(conflicting.isRunning());
        }
    }

    // ------------------------------------------------------------------ helpers

    static void assertError(JsonNode response, String code) {
        assertEquals("ERROR", response.get("type").asText(), response.toString());
        assertFalse(response.get("success").asBoolean());
        assertEquals(code, response.get("code").asText(), response.toString());
        assertTrue(response.hasNonNull("message"));
    }

    static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within 5 s");
            }
            Thread.sleep(20);
        }
    }

    private static InetAddress firstNonLoopbackAddress() {
        try {
            for (var nic : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback()) {
                    continue;
                }
                for (var address : java.util.Collections.list(nic.getInetAddresses())) {
                    if (address instanceof java.net.Inet4Address) {
                        return address;
                    }
                }
            }
        } catch (IOException ignored) {
            // no interfaces to probe
        }
        return null;
    }
}
