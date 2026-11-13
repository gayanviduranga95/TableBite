package com.tablebite.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tablebite.server.api.ApiDispatcher;
import com.tablebite.server.api.CustomerRoutes;
import com.tablebite.server.api.Router;
import com.tablebite.server.api.StaffRoutes;
import com.tablebite.server.api.UploadStore;
import com.tablebite.service.AuthenticationService;
import com.tablebite.service.MenuService;
import com.tablebite.service.OrderService;
import com.tablebite.service.SettingsService;
import com.tablebite.service.TableService;
import com.tablebite.util.LoggerUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * REST API (port HTTP_PORT, default 8080) built on the JDK's HttpServer.
 * Routes live in {@link CustomerRoutes} and {@link StaffRoutes}; uploaded images
 * are served from /uploads.
 */
public final class HttpApiServer {
    private static final Logger LOG = LoggerUtil.get(HttpApiServer.class);
    private final HttpServer server;

    public HttpApiServer(int port) throws IOException {
        this(port, new AuthenticationService());
    }

    public HttpApiServer(int port, AuthenticationService auth) throws IOException {
        MenuService menu = new MenuService();
        TableService tables = new TableService();
        OrderService orders = new OrderService();
        SettingsService settings = new SettingsService();
        UploadStore uploads = new UploadStore();

        Router router = new Router();
        new CustomerRoutes(auth, menu, tables, orders, settings).register(router);
        new StaffRoutes(auth, menu, tables, orders, settings, uploads).register(router);

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api", new ApiDispatcher(router, auth));
        server.createContext("/uploads", (HttpExchange exchange) -> uploads.serve(exchange));
        server.setExecutor(Executors.newFixedThreadPool(16));
        LOG.info("Image uploads stored in " + uploads.directory());
    }

    public void start() {
        server.start();
        LOG.info("TableBite HTTP API started on port " + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(1);
    }
}
