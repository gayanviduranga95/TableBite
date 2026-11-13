package com.tablebite.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tablebite.database.OrderRepository;
import com.tablebite.model.Order;
import com.tablebite.model.User;
import com.tablebite.model.UserRole;
import com.tablebite.service.AuthenticationService;
import com.tablebite.util.JsonUtil;
import com.tablebite.util.LoggerUtil;
import com.tablebite.util.SecurityUtil;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Real-time notification server.
 *
 * Channels (client sends {"type":"SUBSCRIBE", "channel": ...}):
 * <ul>
 *   <li>{@code kitchen} / {@code staff} - every order event; needs a staff session
 *       {@code token} (or the legacy {@code accessCode}).</li>
 *   <li>{@code order} - one order; needs {@code orderId} plus its {@code trackingToken}
 *       (or a session token of the owner / staff).</li>
 *   <li>{@code user} - all orders of a signed-in customer; needs a session {@code token}.</li>
 * </ul>
 */
public class LiveWebSocketServer extends WebSocketServer {
    private static final Logger LOG = LoggerUtil.get(LiveWebSocketServer.class);
    private static volatile LiveWebSocketServer instance;
    private static final ObjectMapper MAPPER = JsonUtil.MAPPER;

    private final Set<WebSocket> staffSubscribers = ConcurrentHashMap.newKeySet();
    private final Map<Long, Set<WebSocket>> orderSubscribers = new ConcurrentHashMap<>();
    private final Map<Long, Set<WebSocket>> userSubscribers = new ConcurrentHashMap<>();
    private final OrderRepository orders = new OrderRepository();
    private final AuthenticationService auth;

    public LiveWebSocketServer(int port) {
        this(port, new AuthenticationService());
    }

    public LiveWebSocketServer(int port, AuthenticationService auth) {
        super(new InetSocketAddress(port));
        this.auth = auth;
        setReuseAddr(true);
        setConnectionLostTimeout(60);
    }

    public static void installInstance(LiveWebSocketServer server) {
        instance = server;
    }

    public static LiveWebSocketServer getInstance() {
        return instance;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        // Legacy clients pass ?accessCode=... in the URL.
        if (auth.legacyStaff(queryValue(conn, "accessCode")) != null) {
            staffSubscribers.add(conn);
        }
        sendJson(conn, "CONNECTED", null, null);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        if (message == null || message.isBlank()) {
            sendError(conn, "EMPTY_MESSAGE", "Message cannot be empty");
            return;
        }
        if (message.length() > 8 * 1024) {
            sendError(conn, "MESSAGE_TOO_LARGE", "Message is too large");
            return;
        }
        try {
            JsonNode request = MAPPER.readTree(message);
            if (request == null || !request.isObject()
                    || !request.hasNonNull("type") || !request.get("type").isTextual()) {
                sendError(conn, "VALIDATION_ERROR", "Missing or invalid message type");
                return;
            }
            String type = request.get("type").asText();
            if ("PING".equalsIgnoreCase(type)) {
                sendJson(conn, "PONG", null, null);
            } else if ("SUBSCRIBE".equalsIgnoreCase(type)) {
                handleSubscription(conn, request);
            } else {
                sendError(conn, "UNKNOWN_MESSAGE_TYPE", "Unsupported WebSocket message type");
            }
        } catch (Exception exception) {
            sendError(conn, "INVALID_JSON", "Message must be valid JSON");
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        staffSubscribers.remove(conn);
        removeFrom(orderSubscribers, conn);
        removeFrom(userSubscribers, conn);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        LOG.warning("WebSocket error: " + ex.getMessage());
        if (conn != null) {
            conn.close();
        }
    }

    @Override
    public void onStart() {
        LOG.info("TableBite WebSocket server started on port " + getPort());
    }

    // ------------------------------------------------------------------ publishing

    /** Sends an order event to staff, to the order's trackers and to its owner. */
    public void publishOrderEvent(String type, Order order, String message) {
        ObjectNode event = MAPPER.createObjectNode();
        event.put("type", type);
        event.put("orderId", order.id());
        event.put("orderNumber", order.orderNumber());
        event.put("status", order.status());
        event.put("paymentStatus", order.paymentStatus());
        event.put("orderType", order.orderType());
        event.put("tableNumber", order.tableNumber());
        event.put("message", message);
        event.set("order", MAPPER.valueToTree(order));
        String payload = event.toString();

        Set<WebSocket> recipients = ConcurrentHashMap.newKeySet();
        recipients.addAll(staffSubscribers);
        recipients.addAll(orderSubscribers.getOrDefault(order.id(), Set.of()));
        if (order.userId() != null) {
            recipients.addAll(userSubscribers.getOrDefault(order.userId(), Set.of()));
        }
        for (WebSocket socket : recipients) {
            if (socket.isOpen()) {
                socket.send(payload);
            }
        }
    }

    // ------------------------------------------------------------------ subscriptions

    private void handleSubscription(WebSocket conn, JsonNode payload) throws Exception {
        String channel = text(payload, "channel");
        if (channel == null) {
            sendError(conn, "VALIDATION_ERROR", "Subscription channel is required");
            return;
        }
        User user = auth.authenticate(text(payload, "token"));
        if (user == null) {
            String code = text(payload, "accessCode");
            user = auth.legacyStaff(code != null ? code : queryValue(conn, "accessCode"));
        }

        switch (channel.toLowerCase()) {
            case "kitchen", "staff" -> {
                if (user == null || !user.role().isStaff()) {
                    sendError(conn, "UNAUTHORIZED", "Staff sign-in is required");
                    conn.close();
                    return;
                }
                staffSubscribers.add(conn);
                sendJson(conn, "SUBSCRIBED", "channel", "staff");
            }
            case "user" -> {
                if (user == null || user.role() != UserRole.CUSTOMER) {
                    sendError(conn, "UNAUTHORIZED", "Sign in to receive order notifications");
                    return;
                }
                userSubscribers.computeIfAbsent(user.id(), ignored -> ConcurrentHashMap.newKeySet()).add(conn);
                sendJson(conn, "SUBSCRIBED", "channel", "user");
            }
            case "order", "orders" -> subscribeToOrder(conn, payload, user);
            default -> sendError(conn, "UNKNOWN_CHANNEL", "Unsupported subscription channel");
        }
    }

    private void subscribeToOrder(WebSocket conn, JsonNode payload, User user) {
        if (!payload.hasNonNull("orderId") || !payload.get("orderId").canConvertToLong()) {
            sendError(conn, "VALIDATION_ERROR", "Order ID is required");
            return;
        }
        long orderId = payload.get("orderId").asLong();
        try {
            Order order = orderId > 0 ? orders.findById(orderId) : null;
            boolean allowed = order != null && (
                    (user != null && user.role().isStaff())
                    || (user != null && order.userId() != null && order.userId() == user.id())
                    || SecurityUtil.constantTimeEquals(text(payload, "trackingToken"), order.trackingToken()));
            if (!allowed) {
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
    }

    // ------------------------------------------------------------------ helpers

    private static void removeFrom(Map<Long, Set<WebSocket>> map, WebSocket conn) {
        map.values().forEach(subscribers -> subscribers.remove(conn));
        map.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) && node.get(field).isTextual() ? node.get(field).asText() : null;
    }

    private String queryValue(WebSocket conn, String key) {
        String descriptor = conn.getResourceDescriptor();
        if (descriptor == null || !descriptor.contains("?")) {
            return null;
        }
        String query = descriptor.substring(descriptor.indexOf('?') + 1);
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && key.equals(parts[0])) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
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
        if (conn.isOpen()) {
            conn.send(response.toString());
        }
    }
}
