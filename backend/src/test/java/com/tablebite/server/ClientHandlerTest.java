package com.tablebite.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tablebite.database.OrderRepository;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientHandlerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void createsOrderAndReturnsGeneratedId() throws Exception {
        AtomicBoolean repositoryCalled = new AtomicBoolean();
        OrderRepository repository = new OrderRepository() {
            @Override
            public long createOrder(long tableId, List<OrderItemInput> items) {
                repositoryCalled.set(true);
                assertEquals(1L, tableId);
                assertEquals(List.of(new OrderItemInput(1, 2)), items);
                return 42L;
            }
        };

        JsonNode response = send("{\"type\":\"NEW_ORDER\",\"tableId\":1,\"items\":[{\"menuItemId\":1,\"quantity\":2}]}", repository);

        assertTrue(repositoryCalled.get());
        assertEquals("ORDER_CREATED", response.get("type").asText());
        assertTrue(response.get("success").asBoolean());
        assertEquals(42L, response.get("orderId").asLong());
        assertEquals("PENDING", response.get("status").asText());
    }

    @Test
    void rejectsInvalidOrderAndKeepsConnectionUsable() throws Exception {
        AtomicBoolean repositoryCalled = new AtomicBoolean();
        OrderRepository repository = new OrderRepository() {
            @Override
            public long createOrder(long tableId, List<OrderItemInput> items) {
                repositoryCalled.set(true);
                return 1L;
            }
        };

        try (ServerSocket serverSocket = new ServerSocket(0);
             Socket client = new Socket("localhost", serverSocket.getLocalPort())) {
            Thread handlerThread = new Thread(() -> acceptAndRun(serverSocket, repository));
            handlerThread.start();
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
                writer.println("{\"type\":\"NEW_ORDER\",\"tableId\":0,\"items\":[]}");
                JsonNode error = MAPPER.readTree(reader.readLine());
                assertEquals("ERROR", error.get("type").asText());
                assertEquals("VALIDATION_ERROR", error.get("code").asText());
                assertFalse(repositoryCalled.get());

                writer.println("{\"type\":\"PING\"}");
                JsonNode pong = MAPPER.readTree(reader.readLine());
                assertEquals("PONG", pong.get("type").asText());
            }
            handlerThread.join(1_000);
        }
    }

    @Test
    void rejectsOversizedPayloadsAndKeepsConnectionUsable() throws Exception {
        OrderRepository repository = new OrderRepository();

        try (ServerSocket serverSocket = new ServerSocket(0);
             Socket client = new Socket("localhost", serverSocket.getLocalPort())) {
            Thread handlerThread = new Thread(() -> acceptAndRun(serverSocket, repository));
            handlerThread.start();
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder payload = new StringBuilder();
                payload.append("{\"type\":\"PING\",\"message\":\"");
                payload.append("x".repeat(200_000));
                payload.append("\"}");

                writer.println(payload);
                JsonNode error = MAPPER.readTree(reader.readLine());
                assertEquals("ERROR", error.get("type").asText());
                assertEquals("MESSAGE_TOO_LARGE", error.get("code").asText());

                writer.println("{\"type\":\"PING\"}");
                JsonNode pong = MAPPER.readTree(reader.readLine());
                assertEquals("PONG", pong.get("type").asText());
            }
            handlerThread.join(1_000);
        }
    }

    private JsonNode send(String request, OrderRepository repository) throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             Socket client = new Socket("localhost", serverSocket.getLocalPort())) {
            Thread handlerThread = new Thread(() -> acceptAndRun(serverSocket, repository));
            handlerThread.start();
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
                writer.println(request);
                JsonNode response = MAPPER.readTree(reader.readLine());
                client.shutdownOutput();
                handlerThread.join(1_000);
                return response;
            }
        }
    }

    private void acceptAndRun(ServerSocket serverSocket, OrderRepository repository) {
        try (Socket socket = serverSocket.accept()) {
            new ClientHandler(socket, repository).run();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
