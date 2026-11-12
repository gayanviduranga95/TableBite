
package com.tablebite;

import com.tablebite.server.HttpApiServer;
import com.tablebite.server.LiveWebSocketServer;
import com.tablebite.server.TcpServer;

import java.util.concurrent.CountDownLatch;

public class Main {
    public static void main(String[] args) throws Exception {
        try {
            int httpPort = Integer.parseInt(
                    System.getenv().getOrDefault("HTTP_PORT", "8080"));
            int tcpPort = Integer.parseInt(
                    System.getenv().getOrDefault("TCP_PORT", "5000"));
            int websocketPort = Integer.parseInt(
                    System.getenv().getOrDefault("WEBSOCKET_PORT", "8081"));

            HttpApiServer httpServer = new HttpApiServer(httpPort);
            httpServer.start();

            LiveWebSocketServer webSocketServer = new LiveWebSocketServer(websocketPort);
            LiveWebSocketServer.installInstance(webSocketServer);
            webSocketServer.start();

            Thread tcpThread = new Thread(() -> new TcpServer(tcpPort).start(), "tablebite-tcp");
            tcpThread.setDaemon(true);
            tcpThread.start();

            CountDownLatch latch = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(latch::countDown));
            latch.await();
        } catch (Exception exception) {
            System.err.println("TableBite startup failed: "
                    + exception.getMessage());
            throw exception;
        }
    }
}
