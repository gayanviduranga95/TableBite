package com.tablebite.server.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.sun.net.httpserver.HttpExchange;
import com.tablebite.model.User;
import com.tablebite.model.UserRole;
import com.tablebite.service.AuthenticationService;
import com.tablebite.service.ServiceException;
import com.tablebite.util.JsonUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/** Per-request helper: parameters, JSON body, authenticated user and responses. */
public final class ApiContext {
    private static final int MAX_JSON_BYTES = 256 * 1024;

    private final HttpExchange exchange;
    private final AuthenticationService auth;
    private final Map<String, String> pathParams;
    private Map<String, String> query;
    private JsonNode body;
    private User user;
    private boolean userResolved;

    ApiContext(HttpExchange exchange, AuthenticationService auth, Map<String, String> pathParams) {
        this.exchange = exchange;
        this.auth = auth;
        this.pathParams = pathParams;
    }

    public HttpExchange exchange() {
        return exchange;
    }

    // ------------------------------------------------------------------ input

    public long pathId(String name) {
        String value = pathParams.get(name);
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a positive integer");
        }
    }

    public String path(String name) {
        return pathParams.get(name);
    }

    public String query(String name) {
        if (query == null) {
            query = new HashMap<>();
            String raw = exchange.getRequestURI().getRawQuery();
            if (raw != null) {
                for (String pair : raw.split("&")) {
                    String[] parts = pair.split("=", 2);
                    if (parts.length == 2) {
                        query.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                                URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
                    }
                }
            }
        }
        String value = query.get(name);
        return value == null || value.isBlank() ? null : value;
    }

    public int queryInt(String name, int fallback) {
        String value = query(name);
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public JsonNode body() throws IOException {
        if (body == null) {
            byte[] bytes = bytes(MAX_JSON_BYTES);
            if (bytes.length == 0) {
                body = NullNode.getInstance();
            } else {
                try {
                    body = JsonUtil.MAPPER.readTree(bytes);
                } catch (JsonProcessingException exception) {
                    throw new IllegalArgumentException("Request body must be valid JSON");
                }
            }
        }
        return body;
    }

    public String bodyText(String field) throws IOException {
        JsonNode node = body().get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    /** Reads the raw body, rejecting payloads larger than {@code maxBytes} (HTTP 413). */
    public byte[] bytes(int maxBytes) throws IOException {
        String declared = header("Content-Length");
        if (declared != null) {
            try {
                if (Long.parseLong(declared) > maxBytes) {
                    throw new ServiceException(413, "PAYLOAD_TOO_LARGE", "Request body is too large");
                }
            } catch (NumberFormatException ignored) {
                // fall through to the streaming check
            }
        }
        try (InputStream input = exchange.getRequestBody()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new ServiceException(413, "PAYLOAD_TOO_LARGE", "Request body is too large");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    public String header(String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    public String userAgent() {
        return header("User-Agent");
    }

    /**
     * Client identifier for rate limiting. X-Forwarded-For is only trusted when the
     * request comes from a local reverse proxy (e.g. the Vite dev server).
     */
    public String clientKey() {
        var remote = exchange.getRemoteAddress().getAddress();
        String forwarded = header("X-Forwarded-For");
        if (remote.isLoopbackAddress() && forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return remote.getHostAddress();
    }

    // ------------------------------------------------------------------ auth

    public String bearerToken() {
        String header = header("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).trim();
        }
        return null;
    }

    /** The signed-in user, or null for guests. */
    public User user() throws SQLException {
        if (!userResolved) {
            user = auth.authenticate(bearerToken());
            String kitchenCode = header("X-Staff-Access-Code");
            if (user == null && kitchenCode != null && !kitchenCode.isBlank()) {
                user = auth.kitchenUser(kitchenCode, clientKey());
            }
            userResolved = true;
        }
        return user;
    }

    public User requireUser() throws SQLException {
        User current = user();
        if (current == null) {
            throw ServiceException.unauthorized("Please sign in to continue");
        }
        return current;
    }

    public User requireCustomer() throws SQLException {
        User current = requireUser();
        if (current.role() != UserRole.CUSTOMER) {
            throw ServiceException.forbidden("This action is only available to customer accounts");
        }
        return current;
    }

    public User requireStaff() throws SQLException {
        User current = requireUser();
        if (!current.role().isStaff()) {
            throw ServiceException.forbidden("Restaurant staff access is required");
        }
        return current;
    }

    public User requireKitchen() throws SQLException {
        User current = requireStaff();
        if (!current.role().canUseKitchen()) {
            throw ServiceException.forbidden("Kitchen access is required");
        }
        return current;
    }

    public User requireAdmin() throws SQLException {
        User current = requireUser();
        if (current.role() != UserRole.ADMIN) {
            throw ServiceException.forbidden("Administrator access is required");
        }
        return current;
    }

    // ------------------------------------------------------------------ output

    public void json(int status, Object value) throws IOException {
        byte[] bytes = JsonUtil.MAPPER.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    public void ok(Object value) throws IOException {
        json(200, value);
    }

    public void created(Object value) throws IOException {
        json(201, value);
    }

    public void noContent() throws IOException {
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }
}
