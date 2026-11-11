
package com.tablebite;

import com.tablebite.server.TcpServer;
import com.tablebite.server.HttpApiServer;

public class Main {
    public static void main(String[] args) {
        try {
            int httpPort = Integer.parseInt(
                    System.getenv().getOrDefault("HTTP_PORT", "8080"));
            HttpApiServer httpServer = new HttpApiServer(httpPort);
            httpServer.start();
            new TcpServer(5000).start();
        } catch (Exception exception) {
            System.err.println("TableBite startup failed: "
                    + exception.getMessage());
        }
    }
}
