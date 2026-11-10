
package com.tablebite.server;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {

    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (
            Socket client = socket;
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                    client.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(
                    client.getOutputStream(), StandardCharsets.UTF_8), true)
        ) {
            String message;

            while ((message = reader.readLine()) != null) {
                System.out.println("Received: " + message);

                writer.println(
                    "{\"type\":\"ACK\",\"message\":\"Received\"}"
                );
            }

        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }
}
