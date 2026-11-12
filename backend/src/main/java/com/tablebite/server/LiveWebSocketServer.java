package com.tablebite.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tablebite.database.OrderRepository;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LiveWebSocketServer extends WebSocketServer {
    private static volatile LiveWebSocketServer instance;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Set<WebSocket> kitchenSubscribers = ConcurrentHashMap.newKeySet();
    private final Map<Long, Set<WebSocket>> orderSubscribers = new ConcurrentHashMap<>();
    private final OrderRepository orders = new OrderRepository();

    public LiveWebSocketServer(int port) {
        super(new InetSocketAddress(port));
    }

    public static void installInstance(LiveWebSocketServer server) {
        instance = server;
    }

    public static LiveWebSocketServer getInstance() {
        return instance;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String accessCode = queryValue(conn, "accessCode");
        if (isKitchenAuthorized(accessCode)) {
            kitchenSubscribers.add(conn);
        }
        System.out.println("WebSocket client connected: " + conn.getRemoteSocketAddress());
        sendJson(conn, "CONNECTED", null, null);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        if (message == null || message.isBlank()) {
            sendError(conn, "EMPTY_MESSAGE", "Message cannot be empty");
            return;
        }
        try {
            JsonNode request = MAPPER.readTree(message);
            if (request == null || !request.isObject()) {
                sendError(conn, "INVALID_JSON", "Request must be a JSON object");
                return;
            }
            if (!request.hasNonNull("type") || !request.get("type").isTextual()) {
                sendError(conn, "VALIDATION_ERROR", "Missing or invalid message type");
                return;
            }
            String type = request.get("type").asText();
            if ("PING".equalsIgnoreCase(type)) {
                sendJson(conn, "PONG", null, null);
                return;
            }
            if ("SUBSCRIBE".equalsIgnoreCase(type)) {
                handleSubscription(conn, request);
                return;
            }
            sendError(conn, "UNKNOWN_MESSAGE_TYPE", "Unsupported WebSocket message type");
        } catch (Exception exception) {
            sendError(conn, "INVALID_JSON", "Message must be valid JSON");
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        kitchenSubscribers.remove(conn);
        orderSubscribers.values().forEach(subscribers -> subscribers.remove(conn));
        orderSubscribers.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        System.out.println("WebSocket client disconnected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("WebSocket error: " + ex.getMessage());
        if (conn != null) {
            conn.close();
        }
    }

    @Override
    public void onStart() {
        System.out.println("TableBite WebSocket server started on port " + getPort());
    }

    public void broadcastOrderCreated(long orderId, String status) {
        ObjectNode event = MAPPER.createObjectNode();
        event.put("type", "ORDER_CREATED");
        event.put("orderId", orderId);
        event.put("status", status);
        broadcastToKitchen(event.toString());
    }

    public void broadcastOrderStatusChanged(long orderId, String status) {
        ObjectNode event = MAPPER.createObjectNode();
        event.put("type", "ORDER_STATUS_CHANGED");
        event.put("orderId", orderId);
        event.put("status", status);
        Set<WebSocket> subscribers = orderSubscribers.get(orderId);
        if (subscribers != null) {
            for (WebSocket subscriber : subscribers) {
                if (subscriber.isOpen()) {
                    subscriber.send(event.toString());
                }
            }
        }
    }

    private void handleSubscription(WebSocket conn, JsonNode payload) {
        if (!payload.hasNonNull("channel") || !payload.get("channel").isTextual()) {
            sendError(conn, "VALIDATION_ERROR", "Subscription channel is required");
            return;
        }
        String channel = payload.get("channel").asText();
        if ("kitchen".equalsIgnoreCase(channel)) {
            String accessCode = payload.hasNonNull("accessCode") && payload.get("accessCode").isTextual()
                    ? payload.get("accessCode").asText()
                    : queryValue(conn, "accessCode");
            if (!isKitchenAuthorized(accessCode)) {
                sendError(conn, "UNAUTHORIZED", "Kitchen access is required");
                conn.close();
                return;
            }
            kitchenSubscribers.add(conn);
            sendJson(conn, "SUBSCRIBED", "channel", "kitchen");
            return;
        }
        if ("order".equalsIgnoreCase(channel) || "orders".equalsIgnoreCase(channel)) {
            if (!payload.hasNonNull("orderId") || !payload.get("orderId").isIntegralNumber()) {
                sendError(conn, "VALIDATION_ERROR", "Order ID is required");
                return;
            }
            long orderId = payload.get("orderId").longValue();
            try {
                if (orders.findById(orderId) == null) {
                    sendError(conn, "ORDER_NOT_FOUND", "Order was not found");
                    return;
                }
            } catch (Exception exception) {
                sendError(conn, "ORDER_UNAVAILABLE", "The order could not be loaded");
                return;
            }
            orderSubscribers.computeIfAbsent(orderId, ignored -> ConcurrentHashMap.newKeySet()).add(conn);
            ObjectNode response = MAPPER.createObjectNode();
            response.put("type", "SUBSCRIBED");
            response.put("channel", "order");
            response.put("orderId", orderId);
            conn.send(response.toString());
            return;
        }
        sendError(conn, "UNKNOWN_CHANNEL", "Unsupported subscription channel");
    }

    private void broadcastToKitchen(String payload) {
        for (WebSocket subscriber : kitchenSubscribers) {
            if (subscriber.isOpen()) {
                subscriber.send(payload);
            }
        }
    }

    private boolean isKitchenAuthorized(String accessCode) {
        String configured = System.getenv("STAFF_ACCESS_CODE");
        return configured != null && !configured.isBlank()
                && configured.equals(accessCode);
    }

    private String queryValue(WebSocket conn, String key) {
        String descriptor = conn.getResourceDescriptor();
        if (descriptor == null || descriptor.isBlank() || !descriptor.contains("?")) {
            return null;
        }
        String query = descriptor.substring(descriptor.indexOf('?') + 1);
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && key.equals(parts[0])) {
                return parts[1];
            }
        }
        return null;
    }

    private void sendJson(WebSocket conn, String type, String key, String value) {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("type", type);
        if (key != null && value != null) {
            response.put(key, value);
        }
        conn.send(response.toString());
    }

    private void sendError(WebSocket conn, String code, String message) {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("type", "ERROR");
        response.put("success", false);
        response.put("code", code);
        response.put("message", message);
        conn.send(response.toString());
    }
}
