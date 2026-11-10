
package com.tablebite;

import com.tablebite.server.TcpServer;

public class Main {
    public static void main(String[] args) {
        TcpServer server = new TcpServer(5000);
        server.start();
    }
}
