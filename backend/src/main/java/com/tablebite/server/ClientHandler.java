
package com.tablebite.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private static final ObjectMapper mapper = new ObjectMapper();

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
                try {
                    JsonNode request = mapper.readTree(message);

                    if (request == null
                            || !request.isObject()
                            || !request.hasNonNull("type")
                            || !request.get("type").isTextual()) {
                        writer.println(
                            "{\"type\":\"ERROR\",\"message\":\"Missing or invalid message type\"}"
                        );
                        continue;
                    }

                    String type = request.get("type").asText();

                    switch (type) {
                        case "PING":
                            writer.println(
                                "{\"type\":\"PONG\",\"message\":\"TableBite server is running\"}"
                            );
                            break;

                        case "NEW_ORDER":
                            writer.println(
                                "{\"type\":\"ACK\",\"message\":\"Order request received\"}"
                            );
                            break;

                        case "ORDER_STATUS_UPDATE":
                            writer.println(
                                "{\"type\":\"ACK\",\"message\":\"Status update received\"}"
                            );
                            break;

                        default:
                            writer.println(
                                "{\"type\":\"ERROR\",\"message\":\"Unknown message type\"}"
                            );
                    }

                } catch (JsonProcessingException e) {
                    writer.println(
                        "{\"type\":\"ERROR\",\"message\":\"Invalid JSON request\"}"
                    );
                }
            }

        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
        }
    }
}
