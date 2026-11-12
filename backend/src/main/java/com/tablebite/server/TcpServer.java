
package com.tablebite.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TcpServer {

    private final int port;
    private final ExecutorService executor =
            Executors.newFixedThreadPool(10);

    public TcpServer(int port) {
        this.port = port;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("TableBite TCP Server started on port " + port);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Socket client = serverSocket.accept();
                    client.setSoTimeout(30_000);
                    client.setKeepAlive(true);
                    System.out.println("TCP client connected: " + client.getRemoteSocketAddress());
                    executor.execute(new ClientHandler(client));
                } catch (IOException exception) {
                    if (!Thread.currentThread().isInterrupted()) {
                        System.err.println("TCP accept failure: " + exception.getMessage());
                    }
                }
            }
        } catch (IOException exception) {
            System.err.println("TCP server error: " + exception.getMessage());
        } finally {
            executor.shutdown();
        }
    }
}
