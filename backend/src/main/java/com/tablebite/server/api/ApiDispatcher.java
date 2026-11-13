package com.tablebite.server.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.tablebite.service.AuthenticationService;
import com.tablebite.service.ServiceException;
import com.tablebite.util.JsonUtil;
import com.tablebite.util.LoggerUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Applies CORS/security headers, dispatches to the router and maps errors to JSON. */
public final class ApiDispatcher implements HttpHandler {
    private static final Logger LOG = LoggerUtil.get(ApiDispatcher.class);
    private final Router router;
    private final AuthenticationService auth;
    private final String allowedOrigin;

    public ApiDispatcher(Router router, AuthenticationService auth) {
        this.router = router;
        this.auth = auth;
        this.allowedOrigin = System.getenv().getOrDefault("CORS_ORIGIN", "*");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            var headers = exchange.getResponseHeaders();
            headers.set("Access-Control-Allow-Origin", allowedOrigin);
            headers.set("Access-Control-Allow-Headers",
                    "Content-Type, Authorization, X-Staff-Access-Code");
            headers.set("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
            headers.set("Access-Control-Max-Age", "600");
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("Referrer-Policy", "no-referrer");

            String method = exchange.getRequestMethod().toUpperCase();
            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String path = exchange.getRequestURI().getPath();
            Router.Match match = router.match(method, path);
            if (match.handler() == null) {
                if (match.pathExists()) {
                    error(exchange, 405, "METHOD_NOT_ALLOWED", "Method is not supported");
                } else {
                    error(exchange, 404, "NOT_FOUND", "Endpoint was not found");
                }
                return;
            }
            ApiContext context = new ApiContext(exchange, auth, match.params());
            try {
                match.handler().handle(context);
            } catch (ServiceException exception) {
                error(exchange, exception.status(), exception.code(), exception.getMessage());
            } catch (IllegalArgumentException exception) {
                error(exchange, 400, "VALIDATION_ERROR", exception.getMessage());
            } catch (SQLException exception) {
                LOG.log(Level.SEVERE, method + " " + path + " database failure (SQLState="
                        + exception.getSQLState() + "): " + exception.getMessage());
                error(exchange, 500, "DATABASE_ERROR", "The request could not be completed. Please try again.");
            } catch (Exception exception) {
                LOG.log(Level.SEVERE, method + " " + path + " failed", exception);
                error(exchange, 500, "SERVER_ERROR", "Something went wrong. Please try again.");
            }
        }
    }

    private void error(HttpExchange exchange, int status, String code, String message) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("code", code);
            body.put("message", message);
            byte[] bytes = JsonUtil.MAPPER.writeValueAsBytes(body);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        } catch (IOException ignored) {
            // response already started or client disconnected
        }
    }
}
