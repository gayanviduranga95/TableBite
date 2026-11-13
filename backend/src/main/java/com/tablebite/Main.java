package com.tablebite;

import com.tablebite.database.DatabaseConfig;
import com.tablebite.database.SchemaMigrator;
import com.tablebite.server.HttpApiServer;
import com.tablebite.server.LiveWebSocketServer;
import com.tablebite.server.TcpServer;
import com.tablebite.service.AuthenticationService;
import com.tablebite.util.LoggerUtil;

import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

/**
 * Starts the three TableBite network servers:
 * HTTP REST API (HTTP_PORT, 8080), WebSocket notifications (WEBSOCKET_PORT, 8081)
 * and the raw TCP protocol server (TCP_PORT, 5000).
 */
public class Main {
    private static final Logger LOG = LoggerUtil.get(Main.class);

    public static void main(String[] args) throws Exception {
        try {
            int httpPort = Integer.parseInt(System.getenv().getOrDefault("HTTP_PORT", "8080"));
            int tcpPort = Integer.parseInt(System.getenv().getOrDefault("TCP_PORT", "5000"));
            int websocketPort = Integer.parseInt(System.getenv().getOrDefault("WEBSOCKET_PORT", "8081"));

            LOG.info("Using database " + DatabaseConfig.url());
            if (!"false".equalsIgnoreCase(System.getenv("DB_AUTO_MIGRATE"))) {
                SchemaMigrator.migrate();
            }
            AuthenticationService auth = new AuthenticationService();
            auth.bootstrapAccounts();

            HttpApiServer httpServer = new HttpApiServer(httpPort, auth);
            httpServer.start();

            LiveWebSocketServer webSocketServer = new LiveWebSocketServer(websocketPort, auth);
            LiveWebSocketServer.installInstance(webSocketServer);
            webSocketServer.start();

            Thread tcpThread = new Thread(() -> new TcpServer(tcpPort).start(), "tablebite-tcp");
            tcpThread.setDaemon(true);
            tcpThread.start();

            CountDownLatch latch = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOG.info("Shutting down TableBite");
                httpServer.stop();
                try {
                    webSocketServer.stop(1000);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                latch.countDown();
            }));
            latch.await();
        } catch (Exception exception) {
            System.err.println("TableBite startup failed: " + exception.getMessage());
            throw exception;
        }
    }
}
