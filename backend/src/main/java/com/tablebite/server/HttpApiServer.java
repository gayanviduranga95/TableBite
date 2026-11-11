package com.tablebite.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tablebite.database.MenuRepository;
import com.tablebite.database.OrderRepository;
import com.tablebite.database.TableRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public final class HttpApiServer {
    private final HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();
    private final OrderRepository orders = new OrderRepository();
    private final MenuRepository menu = new MenuRepository();
    private final TableRepository tables = new TableRepository();

    public HttpApiServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(10));
    }

    public void start() {
        server.start();
        System.out.println("TableBite HTTP API started on port "
                + server.getAddress().getPort());
    }

    private void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers",
                "Content-Type, X-Staff-Access-Code");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,PATCH,OPTIONS");
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 204, null);
            return;
        }

        try {
            String method = exchange.getRequestMethod();
            String[] parts = pathParts(exchange.getRequestURI().getPath());
            if (parts.length == 1 && "menu".equals(parts[0]) && "GET".equals(method)) {
                send(exchange, 200, mapper.valueToTree(menu.findAvailable()));
                return;
            }
            if (parts.length == 2 && "tables".equals(parts[0]) && "GET".equals(method)) {
                TableRepository.RestaurantTable table = tables.findByQrToken(parts[1]);
                if (table == null) {
                    error(exchange, 404, "TABLE_NOT_FOUND", "Table QR code was not found");
                } else {
                    ObjectNode response = mapper.createObjectNode();
                    response.put("id", table.id());
                    response.put("tableNumber", table.tableNumber());
                    response.put("qrToken", table.qrToken());
                    response.put("customerUrl", "/table/" + table.qrToken());
                    send(exchange, 200, response);
                }
                return;
            }
            if (parts.length == 1 && "orders".equals(parts[0]) && "GET".equals(method)) {
                requireStaff(exchange);
                send(exchange, 200, mapper.valueToTree(orders.findAll()));
                return;
            }
            if (parts.length == 1 && "orders".equals(parts[0]) && "POST".equals(method)) {
                createOrder(exchange);
                return;
            }
            if (parts.length == 2 && "orders".equals(parts[0])) {
                long orderId = parseId(parts[1]);
                if ("GET".equals(method)) {
                    OrderRepository.OrderDetails order = orders.findById(orderId);
                    if (order == null) {
                        error(exchange, 404, "ORDER_NOT_FOUND", "Order was not found");
                    } else {
                        send(exchange, 200, mapper.valueToTree(order));
                    }
                } else if ("PATCH".equals(method)) {
                    requireStaff(exchange);
                    updateStatus(exchange, orderId);
                } else {
                    error(exchange, 405, "METHOD_NOT_ALLOWED", "Method is not supported");
                }

                return;
            }
            error(exchange, 404, "NOT_FOUND", "Endpoint was not found");
        } catch (RequestHandledException exception) {
            // The response was already written by the authorization check.
        } catch (IllegalArgumentException exception) {
            error(exchange, 400, "VALIDATION_ERROR", exception.getMessage());
        } catch (SQLException exception) {
            System.err.println("HTTP database failure: "
                    + exception.getClass().getSimpleName()
                    + " (SQLState=" + exception.getSQLState()
                    + "): " + exception.getMessage());
            error(exchange, 500, "DATABASE_ERROR", "The request could not be completed");
        } catch (JsonProcessingException exception) {
            error(exchange, 400, "INVALID_JSON", "Request body must be valid JSON");
        } catch (RuntimeException exception) {
            System.err.println("HTTP request failure: "
                    + exception.getClass().getSimpleName());
            error(exchange, 500, "SERVER_ERROR", "The request could not be completed");
        }
    }

    private void requireStaff(HttpExchange exchange) throws IOException {
        String configured = System.getenv("STAFF_ACCESS_CODE");
        String supplied = exchange.getRequestHeaders().getFirst("X-Staff-Access-Code");
        if (configured == null || configured.isBlank() || supplied == null
                || !MessageDigest.isEqual(
                        configured.getBytes(StandardCharsets.UTF_8),
                        supplied.getBytes(StandardCharsets.UTF_8))) {
            error(exchange, 401, "STAFF_AUTH_REQUIRED",
                    "Internal staff access is required");
            throw new RequestHandledException();
        }
    }

    private void createOrder(HttpExchange exchange)
            throws IOException, SQLException {
        JsonNode body = readJson(exchange);
        String token = requiredText(body, "tableToken");
        TableRepository.RestaurantTable table = tables.findByQrToken(token);
        if (table == null) {
            error(exchange, 404, "TABLE_NOT_FOUND", "Table QR code was not found");
            return;
        }
        JsonNode itemNodes = body.get("items");
        if (itemNodes == null || !itemNodes.isArray() || itemNodes.isEmpty()) {
            throw new IllegalArgumentException("items must be a nonempty array");
        }
        List<OrderRepository.OrderItemInput> items = new ArrayList<>();
        for (JsonNode item : itemNodes) {
            items.add(new OrderRepository.OrderItemInput(
                    requiredPositiveLong(item, "menuItemId"),
                    requiredPositiveInt(item, "quantity")));
        }
        long id = orders.createOrder(table.id(), items);
        ObjectNode response = mapper.createObjectNode();
        response.put("orderId", id);
        response.put("status", "PENDING");
        send(exchange, 201, response);
    }

    private void updateStatus(HttpExchange exchange, long orderId)
            throws IOException, SQLException {
        JsonNode body = readJson(exchange);
        String status = requiredText(body, "status").toUpperCase();
        if (!List.of("PENDING", "PREPARING", "READY", "SERVED", "CANCELLED")
                .contains(status)) {
            throw new IllegalArgumentException("Unsupported order status");
        }
        orders.updateStatus(orderId, status);
        ObjectNode response = mapper.createObjectNode();
        response.put("orderId", orderId);
        response.put("status", status);
        send(exchange, 200, response);
    }

    private JsonNode readJson(HttpExchange exchange)
            throws IOException, JsonProcessingException {
        try (InputStream input = exchange.getRequestBody()) {
            return mapper.readTree(input);
        }
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.asText();
    }

    private long requiredPositiveLong(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() <= 0) {
            throw new IllegalArgumentException(field + " must be a positive integer");
        }
        return value.longValue();
    }

    private int requiredPositiveInt(JsonNode node, String field) {
        long value = requiredPositiveLong(node, field);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(field + " is too large");
        }
        return (int) value;
    }

    private long parseId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Order ID must be a positive integer");
        }
    }

    private String[] pathParts(String path) {
        String suffix = path.substring("/api".length());
        if (suffix.startsWith("/")) {
            suffix = suffix.substring(1);
        }
        return suffix.isBlank() ? new String[0] : suffix.split("/");
    }

    private void send(HttpExchange exchange, int status, JsonNode body)
            throws IOException {
        byte[] bytes = body == null
                ? new byte[0]
                : mapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private void error(HttpExchange exchange, int status, String code, String message)
            throws IOException {
        ObjectNode body = mapper.createObjectNode();
        body.put("success", false);
        body.put("code", code);
        body.put("message", message);
        send(exchange, status, body);
    }

    private static final class RequestHandledException extends RuntimeException {
    }
}
