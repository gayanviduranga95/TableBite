package com.tablebite.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tablebite.database.MenuRepository;
import com.tablebite.database.OrderRepository;
import com.tablebite.database.PaymentRepository;
import com.tablebite.database.SettingsRepository;
import com.tablebite.database.TableRepository;
import com.tablebite.model.Order;
import com.tablebite.model.User;
import com.tablebite.service.AuthenticationService;
import com.tablebite.service.NotificationService;
import com.tablebite.service.OrderService;
import com.tablebite.service.ServiceException;
import com.tablebite.util.LoggerUtil;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serves one TCP connection using the newline-delimited JSON protocol.
 *
 * <p>Each handler runs on a {@link TcpServer} worker thread and owns its socket. It reads
 * with a short SO_TIMEOUT "poll" so it can enforce the idle timeout and react to a server
 * shutdown between messages; every failure is contained to this connection.
 *
 * <p>Security: a connection starts anonymous. Identity comes only from an {@code AUTH}
 * message (a session token or the kitchen access code), verified by
 * {@link AuthenticationService}; role or user fields sent by the client are ignored.
 * Order status changes go through {@link OrderService}, which applies the same role
 * rules as the HTTP API.
 */
public class ClientHandler implements Runnable {
    private static final Logger LOG = LoggerUtil.get(ClientHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    static final int MAX_AUTH_FAILURES = 5;
    private static final String PONG =
            "{\"type\":\"PONG\",\"message\":\"TableBite server is running\"}";

    /** Application services used by the TCP protocol (injectable for tests). */
    record Services(OrderRepository orders, MenuRepository menu, OrderService orderService,
                    AuthenticationService auth) {
        static Services create(AuthenticationService auth) {
            OrderRepository orders = new OrderRepository();
            return new Services(orders, new MenuRepository(),
                    new OrderService(orders, new TableRepository(), new SettingsRepository(),
                            new PaymentRepository()),
                    auth);
        }
    }

    private final Socket socket;
    private final long connectionId;
    private final Services services;
    private final TcpServer.Settings settings;
    private final Consumer<ClientHandler> onClose;
    private final String remote;
    private volatile boolean stopRequested;

    // Per-connection state; only touched by the worker thread running this handler.
    private User user;
    private int authFailures;
    private long requestCount;
    private String closeAfterResponse;

    ClientHandler(Socket socket, long connectionId, Services services,
                  TcpServer.Settings settings, Consumer<ClientHandler> onClose) {
        this.socket = socket;
        this.connectionId = connectionId;
        this.services = services;
        this.settings = settings;
        this.onClose = onClose;
        this.remote = String.valueOf(socket.getRemoteSocketAddress());
    }

    long connectionId() {
        return connectionId;
    }

    /** Asks the handler to finish its current request and close (graceful shutdown). */
    void requestStop() {
        stopRequested = true;
    }

    /** Closes the socket immediately, unblocking any read or write in progress. */
    void forceClose() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closed
        }
    }

    @Override
    public void run() {
        long startedAt = System.nanoTime();
        String reason = "unknown";
        try (Socket client = socket) {
            client.setSoTimeout(settings.pollIntervalMillis());
            Writer out = new BufferedWriter(new OutputStreamWriter(
                    client.getOutputStream(), StandardCharsets.UTF_8));
            TcpLineReader reader = new TcpLineReader(client.getInputStream(),
                    settings.maxMessageBytes(), settings.maxDiscardBytes());
            reason = serve(reader, out);
        } catch (SocketException exception) {
            reason = stopRequested ? "closed during server shutdown"
                    : "connection lost (" + exception.getMessage() + ")";
        } catch (IOException exception) {
            reason = "I/O error (" + exception.getMessage() + ")";
        } catch (RuntimeException exception) {
            reason = "handler failure (" + exception.getClass().getSimpleName() + ")";
            LOG.log(Level.SEVERE, "TCP #" + connectionId + " handler failed", exception);
        } finally {
            onClose.accept(this);
            LOG.info("TCP #" + connectionId + " closed " + remote + ": " + reason
                    + " (requests=" + requestCount + ", duration="
                    + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt) + " ms)");
        }
    }

    private String serve(TcpLineReader reader, Writer out) throws IOException {
        long idleNanos = TimeUnit.MILLISECONDS.toNanos(settings.idleTimeoutMillis());
        long lastMessageAt = System.nanoTime();
        while (true) {
            if (stopRequested) {
                send(out, "{\"type\":\"SERVER_SHUTDOWN\",\"message\":\"TableBite server is shutting down\"}");
                return "server shutting down";
            }
            TcpLineReader.Result result = reader.next();
            switch (result.status()) {
                case END_OF_STREAM -> {
                    return reader.hasPartialMessage()
                            ? "client disconnected mid-message" : "client disconnected";
                }
                case PENDING -> {
                    // Measured from the last complete message, so a client trickling a
                    // partial line byte by byte cannot keep the connection alive forever.
                    if (System.nanoTime() - lastMessageAt > idleNanos) {
                        send(out, error("IDLE_TIMEOUT", "Connection closed after "
                                + settings.idleTimeoutMillis() / 1000.0 + " s without a complete message"));
                        return "idle timeout";
                    }
                }
                case TOO_LARGE_FATAL -> {
                    LOG.warning("TCP #" + connectionId + " sent more than "
                            + settings.maxDiscardBytes() + " bytes without a newline");
                    send(out, error("MESSAGE_TOO_LARGE",
                            "Message exceeds the maximum supported size; closing connection"));
                    return "unterminated oversized message";
                }
                case TOO_LARGE -> {
                    lastMessageAt = System.nanoTime();
                    requestCount++;
                    LOG.warning("TCP #" + connectionId + " rejected a " + result.size()
                            + "-byte message (limit " + settings.maxMessageBytes() + ")");
                    send(out, error("MESSAGE_TOO_LARGE", "Message exceeds the maximum supported size of "
                            + settings.maxMessageBytes() + " bytes"));
                }
                case LINE -> {
                    lastMessageAt = System.nanoTime();
                    if (result.text().isBlank()) {
                        continue;
                    }
                    requestCount++;
                    send(out, process(result.text()));
                    if (closeAfterResponse != null) {
                        return closeAfterResponse;
                    }
                }
            }
        }
    }

    private static void send(Writer out, String message) throws IOException {
        out.write(message);
        out.write('\n');
        out.flush();
    }

    // ------------------------------------------------------------------ requests

    /** Turns one request line into one response line. Never throws. */
    String process(String message) {
        JsonNode request;
        try {
            request = MAPPER.readTree(message);
        } catch (JsonProcessingException exception) {
            return error("INVALID_JSON", "Invalid JSON request");
        }
        if (request == null || !request.isObject() || !request.hasNonNull("type")
                || !request.get("type").isTextual()) {
            return error("VALIDATION_ERROR", "Missing or invalid message type");
        }
        String type = request.get("type").asText();
        try {
            return dispatch(type, request);
        } catch (ServiceException exception) {
            return error(exception.code(), exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return error("VALIDATION_ERROR", exception.getMessage());
        } catch (SQLException exception) {
            LOG.warning("TCP #" + connectionId + " database failure for " + type + ": "
                    + exception.getClass().getSimpleName() + " (SQLState="
                    + exception.getSQLState() + "): " + exception.getMessage());
            return databaseError(type);
        } catch (RuntimeException exception) {
            LOG.log(Level.WARNING, "TCP #" + connectionId + " failed to process " + type, exception);
            return error("INTERNAL_ERROR", "The request could not be processed");
        }
    }

    private String dispatch(String type, JsonNode request) throws SQLException {
        return switch (type) {
            case "PING" -> PONG;
            case "AUTH" -> handleAuth(request);
            case "GET_MENU" -> json(services.menu().findAvailable());
            case "NEW_ORDER" -> handleNewOrder(request);
            case "ORDER_STATUS_UPDATE" -> handleStatusUpdate(request);
            case "GET_ORDERS" -> {
                requireStaff();
                yield json(services.orders().findAll().stream().map(Order::withoutToken).toList());
            }
            case "GET_ORDER" -> handleGetOrder(request, false);
            case "GET_ORDER_STATUS" -> handleGetOrder(request, true);
            default -> error("UNKNOWN_MESSAGE_TYPE", "Unknown message type");
        };
    }

    private String handleAuth(JsonNode request) throws SQLException {
        String token = optionalText(request.get("token"), "token");
        String accessCode = optionalText(request.get("accessCode"), "accessCode");
        if (token == null && accessCode == null) {
            throw new IllegalArgumentException("token or accessCode is required");
        }
        User authenticated;
        if (token != null) {
            authenticated = services.auth().authenticate(token);
        } else {
            try {
                // Shares the HTTP lock-out for repeated wrong codes from the same address.
                authenticated = services.auth().kitchenUser(accessCode, clientKey());
            } catch (ServiceException exception) {
                if (exception.status() != 401) {
                    throw exception;
                }
                authenticated = null;
            }
        }
        if (authenticated == null || !authenticated.active()) {
            user = null;
            authFailures++;
            LOG.warning("TCP #" + connectionId + " failed AUTH attempt " + authFailures
                    + " from " + remote);
            if (authFailures >= MAX_AUTH_FAILURES) {
                closeAfterResponse = "too many failed AUTH attempts";
            }
            return error("AUTH_FAILED", "Invalid or expired credentials");
        }
        user = authenticated;
        LOG.info("TCP #" + connectionId + " authenticated as " + user.role()
                + (user.id() == 0 ? " (access code)" : " user #" + user.id()));
        ObjectNode response = MAPPER.createObjectNode();
        response.put("type", "AUTH_OK");
        response.put("success", true);
        response.put("userId", user.id());
        response.put("role", user.role().name());
        response.put("name", user.fullName());
        return json(response);
    }

    private String handleNewOrder(JsonNode request) throws SQLException {
        String tableToken = optionalText(request.get("tableToken"), "tableToken");
        if (tableToken != null) {
            // Same path as the web app: the QR token proves the table, and the order is
            // linked to the authenticated customer (if any), never to a client-sent id.
            OrderService.PlaceOrderResult result = services.orderService().placeOrder(
                    new OrderService.PlaceOrderRequest("DINE_IN", tableToken,
                            parseItems(request.get("items")),
                            optionalText(request.get("specialInstructions"), "specialInstructions"),
                            null, null, "PAY_AT_COUNTER",
                            optionalText(request.get("idempotencyKey"), "idempotencyKey")),
                    user);
            Order order = result.order();
            ObjectNode response = MAPPER.createObjectNode();
            response.put("type", "ORDER_CREATED");
            response.put("success", true);
            response.put("orderId", order.id());
            response.put("orderNumber", order.orderNumber());
            response.put("status", order.status());
            response.put("trackingToken", order.trackingToken());
            response.put("duplicate", result.duplicate());
            return json(response);
        }
        if (request.hasNonNull("tableId")) {
            // A raw table id is not proof of being at the table, so only staff may use it.
            requireStaff();
            long tableId = positiveLong(request.get("tableId"), "tableId");
            long orderId = services.orders().createOrder(tableId, parseItems(request.get("items")));
            NotificationService.orderChanged(orderId, true);
            ObjectNode response = MAPPER.createObjectNode();
            response.put("type", "ORDER_CREATED");
            response.put("success", true);
            response.put("orderId", orderId);
            response.put("status", "PENDING");
            return json(response);
        }
        throw new IllegalArgumentException("tableToken is required");
    }

    private String handleStatusUpdate(JsonNode request) throws SQLException {
        User staff = requireStaff();
        long orderId = positiveLong(request.get("orderId"), "orderId");
        String status = text(request.get("status"), "status");
        Order updated = services.orderService().updateStatus(orderId, status, staff,
                optionalText(request.get("reason"), "reason"));
        ObjectNode response = MAPPER.createObjectNode();
        response.put("type", "ORDER_STATUS_UPDATED");
        response.put("orderId", orderId);
        response.put("status", updated.status());
        return json(response);
    }

    private String handleGetOrder(JsonNode request, boolean statusOnly) throws SQLException {
        long orderId = positiveLong(request.get("orderId"), "orderId");
        String trackingToken = optionalText(request.get("trackingToken"), "trackingToken");
        if (user == null && trackingToken == null) {
            throw ServiceException.unauthorized(
                    "Send AUTH first or include the order's trackingToken");
        }
        Order order;
        try {
            // Staff, the signed-in owner, or the holder of the tracking token.
            order = services.orderService().getForViewer(orderId, trackingToken, user);
        } catch (ServiceException exception) {
            if (exception.status() == 404) {
                return error("ORDER_NOT_FOUND", "Order was not found");
            }
            throw exception;
        }
        if (statusOnly) {
            ObjectNode response = MAPPER.createObjectNode();
            response.put("type", "ORDER_STATUS");
            response.put("orderId", order.id());
            response.put("status", order.status());
            return json(response);
        }
        return json(order.withoutToken());
    }

    private User requireStaff() {
        if (user == null) {
            throw ServiceException.unauthorized(
                    "Authentication required: send AUTH with a staff session token or kitchen access code");
        }
        if (!user.role().isStaff()) {
            throw ServiceException.forbidden("Staff access is required");
        }
        return user;
    }

    private String clientKey() {
        return socket.getInetAddress() == null ? remote : socket.getInetAddress().getHostAddress();
    }

    // ------------------------------------------------------------------ parsing

    private List<OrderRepository.OrderItemInput> parseItems(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new IllegalArgumentException("items must be a nonempty array");
        }
        if (node.size() > OrderRepository.MAX_LINES) {
            throw new IllegalArgumentException("An order can have at most "
                    + OrderRepository.MAX_LINES + " lines");
        }
        List<OrderRepository.OrderItemInput> items = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isObject()) {
                throw new IllegalArgumentException("each item must be an object");
            }
            items.add(new OrderRepository.OrderItemInput(
                    positiveLong(item.get("menuItemId"), "menuItemId"),
                    positiveInt(item.get("quantity"), "quantity"),
                    optionalText(item.get("notes"), "notes")));
        }
        return items;
    }

    private static long positiveLong(JsonNode value, String field) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() <= 0) {
            throw new IllegalArgumentException(field + " must be a positive integer");
        }
        return value.longValue();
    }

    private static int positiveInt(JsonNode value, String field) {
        long result = positiveLong(value, field);
        if (result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(field + " is too large");
        }
        return (int) result;
    }

    private static String text(JsonNode value, String field) {
        String result = optionalText(value, field);
        if (result == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return result;
    }

    private static String optionalText(JsonNode value, String field) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return value.asText().isBlank() ? null : value.asText();
    }

    // ------------------------------------------------------------------ responses

    private static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String databaseError(String type) {
        return switch (type) {
            case "NEW_ORDER" -> error("ORDER_CREATION_FAILED", "The order could not be created");
            case "ORDER_STATUS_UPDATE" -> error("ORDER_STATUS_UPDATE_FAILED",
                    "The order status could not be updated");
            case "GET_ORDERS" -> error("ORDERS_FETCH_FAILED", "Orders could not be loaded");
            case "GET_ORDER", "GET_ORDER_STATUS" -> error("ORDER_FETCH_FAILED",
                    "The order could not be loaded");
            case "GET_MENU" -> error("MENU_FETCH_FAILED", "The menu could not be loaded");
            default -> error("SERVICE_UNAVAILABLE", "The request could not be completed");
        };
    }

    /** The single error format used for every TCP failure response. */
    static String error(String code, String message) {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("type", "ERROR");
        response.put("success", false);
        response.put("code", code);
        response.put("message", message);
        try {
            return MAPPER.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            return "{\"type\":\"ERROR\",\"success\":false,\"code\":\"INTERNAL_ERROR\"}";
        }
    }
}
