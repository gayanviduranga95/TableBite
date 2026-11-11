package com.tablebite.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tablebite.database.MenuRepository;
import com.tablebite.database.OrderRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final OrderRepository orderRepository;
    private final MenuRepository menuRepository = new MenuRepository();
    private static final ObjectMapper mapper = new ObjectMapper();

    public ClientHandler(Socket socket) {
        this(socket, new OrderRepository());
    }

    ClientHandler(Socket socket, OrderRepository orderRepository) {
        this.socket = socket;
        this.orderRepository = orderRepository;
    }

    @Override
    public void run() {
        try (Socket client = socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     client.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                     client.getOutputStream(), StandardCharsets.UTF_8), true)) {
            String message;
            while ((message = reader.readLine()) != null) {
                try {
                    JsonNode request = mapper.readTree(message);
                    if (request == null || !request.isObject()
                            || !request.hasNonNull("type")
                            || !request.get("type").isTextual()) {
                        writeError(writer, "VALIDATION_ERROR",
                                "Missing or invalid message type");
                        continue;
                    }
                    handleRequest(request, writer);
                } catch (JsonProcessingException exception) {
                    writeError(writer, "INVALID_JSON", "Invalid JSON request");
                }
            }
        } catch (IOException exception) {
            System.err.println("Client error: " + exception.getMessage());
        }
    }

    private void handleRequest(JsonNode request, PrintWriter writer) {
        switch (request.get("type").asText()) {
            case "PING" -> writer.println(
                    "{\"type\":\"PONG\",\"message\":\"TableBite server is running\"}");
            case "NEW_ORDER" -> handleNewOrder(request, writer);
            case "ORDER_STATUS_UPDATE" -> handleStatusUpdate(request, writer);
            case "GET_ORDERS" -> handleGetOrders(writer);
            case "GET_ORDER", "GET_ORDER_STATUS" ->
                    handleGetOrder(request, writer,
                            "GET_ORDER_STATUS".equals(request.get("type").asText()));
            case "GET_MENU" -> handleGetMenu(writer);
            default -> writeError(writer, "UNKNOWN_MESSAGE_TYPE",
                    "Unknown message type");
        }
    }

    private void handleNewOrder(JsonNode request, PrintWriter writer) {
        try {
            List<OrderRepository.OrderItemInput> items = parseItems(request.get("items"));
            long orderId = orderRepository.createOrder(
                    positiveLong(request.get("tableId"), "tableId"), items);
            ObjectNode response = mapper.createObjectNode();
            response.put("type", "ORDER_CREATED");
            response.put("success", true);
            response.put("orderId", orderId);
            response.put("status", "PENDING");
            writer.println(mapper.writeValueAsString(response));
        } catch (IllegalArgumentException exception) {
            writeError(writer, "VALIDATION_ERROR", exception.getMessage());
        } catch (SQLException | JsonProcessingException exception) {
            if (exception instanceof SQLException sqlException) {
                System.err.println("TCP database failure: "
                        + sqlException.getClass().getSimpleName()
                        + " (SQLState=" + sqlException.getSQLState()
                        + "): " + sqlException.getMessage());
            }
            writeError(writer, "ORDER_CREATION_FAILED",
                    "The order could not be created");
        }
    }

    private void handleStatusUpdate(JsonNode request, PrintWriter writer) {
        try {
            long orderId = positiveLong(request.get("orderId"), "orderId");
            String status = text(request.get("status"), "status").toUpperCase();
            validateStatus(status);
            orderRepository.updateStatus(orderId, status);
            ObjectNode response = mapper.createObjectNode();
            response.put("type", "ORDER_STATUS_UPDATED");
            response.put("orderId", orderId);
            response.put("status", status);
            writer.println(mapper.writeValueAsString(response));
        } catch (IllegalArgumentException exception) {
            writeError(writer, "VALIDATION_ERROR", exception.getMessage());
        } catch (SQLException | JsonProcessingException exception) {
            if (exception instanceof SQLException sqlException) {
                System.err.println("TCP database failure: "
                        + sqlException.getClass().getSimpleName()
                        + " (SQLState=" + sqlException.getSQLState()
                        + "): " + sqlException.getMessage());
            }
            writeError(writer, "ORDER_STATUS_UPDATE_FAILED",
                    "The order status could not be updated");
        }
    }

    private void handleGetOrders(PrintWriter writer) {
        try {
            writer.println(mapper.writeValueAsString(orderRepository.findAll()));
        } catch (SQLException | JsonProcessingException exception) {
            writeError(writer, "ORDERS_FETCH_FAILED", "Orders could not be loaded");
        }
    }

    private void handleGetOrder(JsonNode request, PrintWriter writer, boolean statusOnly) {
        try {
            OrderRepository.OrderDetails order = orderRepository.findById(
                    positiveLong(request.get("orderId"), "orderId"));
            if (order == null) {
                writeError(writer, "ORDER_NOT_FOUND", "Order was not found");
                return;
            }
            if (statusOnly) {
                ObjectNode response = mapper.createObjectNode();
                response.put("type", "ORDER_STATUS");
                response.put("orderId", order.id());
                response.put("status", order.status());
                writer.println(mapper.writeValueAsString(response));
            } else {
                writer.println(mapper.writeValueAsString(order));
            }
        } catch (IllegalArgumentException exception) {
            writeError(writer, "VALIDATION_ERROR", exception.getMessage());
        } catch (SQLException | JsonProcessingException exception) {
            writeError(writer, "ORDER_FETCH_FAILED", "The order could not be loaded");
        }
    }

    private void handleGetMenu(PrintWriter writer) {
        try {
            writer.println(mapper.writeValueAsString(menuRepository.findAvailable()));
        } catch (SQLException | JsonProcessingException exception) {
            writeError(writer, "MENU_FETCH_FAILED", "The menu could not be loaded");
        }
    }

    private List<OrderRepository.OrderItemInput> parseItems(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new IllegalArgumentException("items must be a nonempty array");
        }
        List<OrderRepository.OrderItemInput> items = new ArrayList<>();
        for (JsonNode item : node) {
            items.add(new OrderRepository.OrderItemInput(
                    positiveLong(item.get("menuItemId"), "menuItemId"),
                    positiveInt(item.get("quantity"), "quantity")));
        }
        return items;
    }

    private long positiveLong(JsonNode value, String field) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() <= 0) {
            throw new IllegalArgumentException(field + " must be a positive integer");
        }
        return value.longValue();
    }

    private int positiveInt(JsonNode value, String field) {
        long result = positiveLong(value, field);
        if (result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(field + " is too large");
        }
        return (int) result;
    }

    private String text(JsonNode value, String field) {
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.asText();
    }

    private void validateStatus(String status) {
        if (!List.of("PENDING", "ACCEPTED", "PREPARING", "READY", "SERVED", "CANCELLED")
                .contains(status)) {
            throw new IllegalArgumentException("Unsupported order status");
        }
    }

    private void writeError(PrintWriter writer, String code, String message) {
        ObjectNode response = mapper.createObjectNode();
        response.put("type", "ERROR");
        response.put("success", false);
        response.put("code", code);
        response.put("message", message);
        try {
            writer.println(mapper.writeValueAsString(response));
        } catch (JsonProcessingException exception) {
            writer.println("{\"type\":\"ERROR\",\"success\":false}");
        }
    }
}
