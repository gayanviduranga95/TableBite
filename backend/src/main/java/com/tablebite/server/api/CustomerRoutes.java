package com.tablebite.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.tablebite.database.OrderRepository;
import com.tablebite.model.Order;
import com.tablebite.model.RestaurantTable;
import com.tablebite.model.User;
import com.tablebite.service.AuthenticationService;
import com.tablebite.service.MenuService;
import com.tablebite.service.OrderService;
import com.tablebite.service.SettingsService;
import com.tablebite.service.TableService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Public, customer and authentication endpoints. */
public final class CustomerRoutes {
    private final AuthenticationService auth;
    private final MenuService menu;
    private final TableService tables;
    private final OrderService orders;
    private final SettingsService settings;

    public CustomerRoutes(AuthenticationService auth, MenuService menu, TableService tables,
                          OrderService orders, SettingsService settings) {
        this.auth = auth;
        this.menu = menu;
        this.tables = tables;
        this.orders = orders;
        this.settings = settings;
    }

    public void register(Router router) {
        router.get("/api/health", ctx -> ctx.ok(Map.of("status", "UP")));
        router.get("/api/settings", ctx -> ctx.ok(settings.publicSettings()));

        // ---------- menu
        router.get("/api/menu", ctx -> ctx.ok(menu.customerMenu()));
        router.get("/api/menu/{id}", ctx -> ctx.ok(menu.get(ctx.pathId("id"))));
        router.get("/api/categories", ctx -> ctx.ok(menu.categories(false)));

        // ---------- tables (QR)
        router.post("/api/tables/lookup", ctx -> {
            Map<String, Object> config = settings.publicSettings();
            if (!"true".equals(String.valueOf(config.get("allow_manual_table_selection")))) {
                throw new com.tablebite.service.ServiceException(403, "MANUAL_SELECTION_DISABLED",
                        "Manual table selection is turned off. Please scan the QR code on your table.");
            }
            ctx.ok(publicTable(tables.resolveNumber(ctx.bodyText("tableNumber"))));
        });
        router.get("/api/tables/{token}", ctx -> ctx.ok(publicTable(tables.resolveToken(ctx.path("token")))));

        // ---------- auth
        router.post("/api/auth/register", ctx -> ctx.created(auth.register(
                ctx.bodyText("fullName"), ctx.bodyText("email"), ctx.bodyText("phone"),
                ctx.bodyText("password"), ctx.userAgent())));
        router.post("/api/auth/login", ctx -> ctx.ok(auth.login(
                ctx.bodyText("email"), ctx.bodyText("password"), ctx.userAgent(), ctx.clientKey())));
        router.post("/api/auth/logout", ctx -> {
            auth.logout(ctx.bearerToken());
            ctx.noContent();
        });
        router.get("/api/auth/me", ctx -> ctx.ok(ctx.requireUser()));
        router.put("/api/auth/me", ctx -> {
            User user = ctx.requireUser();
            if (user.id() == 0) {
                throw new IllegalArgumentException("The shared staff code has no profile");
            }
            ctx.ok(auth.updateProfile(user, ctx.bodyText("fullName"), ctx.bodyText("email"),
                    ctx.bodyText("phone")));
        });
        router.put("/api/auth/password", ctx -> {
            User user = ctx.requireUser();
            if (user.id() == 0) {
                throw new IllegalArgumentException("The shared staff code has no password");
            }
            auth.changePassword(user, ctx.bodyText("currentPassword"), ctx.bodyText("newPassword"));
            ctx.noContent();
        });
        router.get("/api/me/orders", ctx -> ctx.ok(orders.ordersForCustomer(ctx.requireCustomer())));

        // ---------- orders
        router.post("/api/orders", ctx -> {
            OrderService.PlaceOrderResult result = orders.placeOrder(parseOrder(ctx.body()), ctx.user());
            Order order = result.order();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("orderId", order.id());
            response.put("orderNumber", order.orderNumber());
            response.put("status", order.status());
            response.put("trackingToken", order.trackingToken());
            response.put("duplicate", result.duplicate());
            response.put("order", order);
            ctx.json(result.duplicate() ? 200 : 201, response);
        });
        router.get("/api/orders/{id}", ctx -> {
            User viewer = ctx.user();
            Order order = orders.getForViewer(ctx.pathId("id"), ctx.query("token"), viewer);
            ctx.ok(viewer != null && viewer.role().isStaff() ? order.withoutToken() : order);
        });
        router.post("/api/orders/{id}/cancel", ctx -> ctx.ok(orders.cancelByCustomer(
                ctx.pathId("id"), ctx.bodyText("token"), ctx.user(), ctx.bodyText("reason"))));
    }

    private static Map<String, Object> publicTable(RestaurantTable table) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", table.id());
        response.put("tableNumber", table.tableNumber());
        response.put("qrToken", table.qrToken());
        response.put("seats", table.seats());
        response.put("status", table.status());
        response.put("location", table.location());
        response.put("customerUrl", "/table/" + table.qrToken());
        return response;
    }

    static OrderService.PlaceOrderRequest parseOrder(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("Request body must be a JSON object");
        }
        JsonNode itemNodes = body.get("items");
        if (itemNodes == null || !itemNodes.isArray() || itemNodes.isEmpty()) {
            throw new IllegalArgumentException("items must be a nonempty array");
        }
        List<OrderRepository.OrderItemInput> items = new ArrayList<>();
        for (JsonNode item : itemNodes) {
            items.add(new OrderRepository.OrderItemInput(
                    positive(item, "menuItemId"),
                    (int) Math.min(positive(item, "quantity"), Integer.MAX_VALUE),
                    item.hasNonNull("notes") ? item.get("notes").asText() : null));
        }
        return new OrderService.PlaceOrderRequest(
                text(body, "orderType"), text(body, "tableToken"), items,
                text(body, "specialInstructions"), text(body, "customerName"),
                text(body, "customerPhone"), text(body, "paymentMethod"),
                text(body, "idempotencyKey"));
    }

    private static long positive(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            throw new IllegalArgumentException(field + " must be a positive integer");
        }
        return value.longValue();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
