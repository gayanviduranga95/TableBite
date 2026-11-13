package com.tablebite.server.api;

import com.tablebite.database.OrderRepository;
import com.tablebite.database.PaymentRepository;
import com.tablebite.database.StatsRepository;
import com.tablebite.model.User;
import com.tablebite.payment.PaymentGateway;
import com.tablebite.payment.PaymentGateways;
import com.tablebite.service.AuthenticationService;
import com.tablebite.service.MenuService;
import com.tablebite.service.NotificationService;
import com.tablebite.service.OrderService;
import com.tablebite.service.ServiceException;
import com.tablebite.service.SettingsService;
import com.tablebite.service.TableService;

import java.util.HashMap;
import java.util.Map;

/** Staff (kitchen / floor) and administrator endpoints. */
public final class StaffRoutes {
    private final AuthenticationService auth;
    private final MenuService menu;
    private final TableService tables;
    private final OrderService orders;
    private final SettingsService settings;
    private final PaymentRepository payments = new PaymentRepository();
    private final StatsRepository stats = new StatsRepository();
    private final OrderRepository orderRepository = new OrderRepository();
    private final UploadStore uploads;

    public StaffRoutes(AuthenticationService auth, MenuService menu, TableService tables,
                       OrderService orders, SettingsService settings, UploadStore uploads) {
        this.auth = auth;
        this.menu = menu;
        this.tables = tables;
        this.orders = orders;
        this.settings = settings;
        this.uploads = uploads;
    }

    public void register(Router router) {
        // ---------- orders (all staff)
        router.get("/api/orders", ctx -> {
            ctx.requireStaff();
            ctx.ok(orders.listForStaff(new OrderRepository.OrderFilter(
                    ctx.query("status"), ctx.query("q"), ctx.query("type"), null, null,
                    "today".equals(ctx.query("range")), "true".equals(ctx.query("active")),
                    ctx.query("payment"), ctx.queryInt("limit", 200)),
                    "true".equals(ctx.query("items"))));
        });
        router.patch("/api/orders/{id}", ctx -> {
            User staff = ctx.requireStaff();
            ctx.ok(orders.updateStatus(ctx.pathId("id"), ctx.bodyText("status"), staff,
                    ctx.bodyText("reason")));
        });
        router.post("/api/orders/{id}/payments", ctx -> {
            User staff = ctx.requireStaff();
            ctx.ok(orders.confirmPayment(ctx.pathId("id"), ctx.bodyText("method"),
                    ctx.bodyText("reference"), staff));
        });

        // ---------- kitchen display
        router.get("/api/kitchen/orders", ctx -> {
            ctx.requireKitchen();
            ctx.ok(orders.listForStaff(new OrderRepository.OrderFilter(
                    null, null, null, null, null, false, true, null, 200), true));
        });

        // ---------- online payment webhook (only active when a gateway is configured)
        router.post("/api/payments/webhook", ctx -> {
            PaymentGateway gateway = PaymentGateways.configured().orElseThrow(() ->
                    new ServiceException(404, "PAYMENTS_NOT_CONFIGURED",
                            "Online payments are not configured on this server"));
            Map<String, String> headers = new HashMap<>();
            ctx.exchange().getRequestHeaders().forEach((key, values) ->
                    headers.put(key.toLowerCase(), values.isEmpty() ? "" : values.get(0)));
            PaymentGateway.WebhookResult result;
            try {
                result = gateway.verifyWebhook(headers, ctx.bytes(64 * 1024));
            } catch (PaymentGateway.PaymentException exception) {
                throw new ServiceException(400, "INVALID_WEBHOOK", exception.getMessage());
            }
            if (result.paid()) {
                payments.confirmPayment(result.orderId(), "ONLINE", result.providerReference(), null);
                NotificationService.paymentUpdated(orderRepository.findById(result.orderId()));
            }
            ctx.ok(Map.of("received", true));
        });

        // ---------- admin: dashboard
        router.get("/api/admin/stats", ctx -> {
            ctx.requireAdmin();
            StatsRepository.Dashboard dashboard = stats.dashboard(ctx.queryInt("days", 14));
            Map<String, Object> response = new HashMap<>();
            response.put("totals", dashboard.totals());
            response.put("revenueByDay", dashboard.revenueByDay());
            response.put("popularItems", dashboard.popularItems());
            response.put("ordersByHour", dashboard.ordersByHour());
            response.put("recentOrders", orders.listForStaff(new OrderRepository.OrderFilter(
                    null, null, null, null, null, false, false, null, 8), false));
            ctx.ok(response);
        });

        // ---------- admin: menu
        router.get("/api/admin/menu", ctx -> {
            ctx.requireAdmin();
            ctx.ok(menu.adminMenu());
        });
        router.post("/api/admin/menu", ctx -> {
            ctx.requireAdmin();
            ctx.created(menu.create(ctx.body()));
        });
        router.put("/api/admin/menu/{id}", ctx -> {
            ctx.requireAdmin();
            ctx.ok(menu.update(ctx.pathId("id"), ctx.body()));
        });
        router.patch("/api/admin/menu/{id}/availability", ctx -> {
            ctx.requireStaff(); // kitchen can mark dishes sold out
            ctx.ok(menu.setAvailability(ctx.pathId("id"), ctx.body().path("available").asBoolean(true)));
        });
        router.delete("/api/admin/menu/{id}", ctx -> {
            ctx.requireAdmin();
            menu.delete(ctx.pathId("id"));
            ctx.noContent();
        });
        router.post("/api/admin/uploads", ctx -> {
            ctx.requireAdmin();
            ctx.created(Map.of("url", uploads.save(ctx.bytes(UploadStore.MAX_BYTES))));
        });

        // ---------- admin: categories
        router.get("/api/admin/categories", ctx -> {
            ctx.requireAdmin();
            ctx.ok(menu.categories(true));
        });
        router.post("/api/admin/categories", ctx -> {
            ctx.requireAdmin();
            ctx.created(menu.createCategory(ctx.body()));
        });
        router.put("/api/admin/categories/{id}", ctx -> {
            ctx.requireAdmin();
            ctx.ok(menu.updateCategory(ctx.pathId("id"), ctx.body()));
        });
        router.delete("/api/admin/categories/{id}", ctx -> {
            ctx.requireAdmin();
            menu.deleteCategory(ctx.pathId("id"));
            ctx.noContent();
        });

        // ---------- tables (floor staff can view, admin manages)
        router.get("/api/admin/tables", ctx -> {
            ctx.requireStaff();
            ctx.ok(tables.list());
        });
        router.post("/api/admin/tables", ctx -> {
            ctx.requireAdmin();
            ctx.created(tables.create(ctx.body()));
        });
        router.put("/api/admin/tables/{id}", ctx -> {
            ctx.requireAdmin();
            ctx.ok(tables.update(ctx.pathId("id"), ctx.body()));
        });
        router.delete("/api/admin/tables/{id}", ctx -> {
            ctx.requireAdmin();
            tables.delete(ctx.pathId("id"));
            ctx.noContent();
        });
        router.post("/api/admin/tables/{id}/regenerate", ctx -> {
            ctx.requireAdmin();
            ctx.ok(tables.regenerateToken(ctx.pathId("id")));
        });
        router.get("/api/admin/tables/{id}/orders", ctx -> {
            ctx.requireStaff();
            ctx.ok(tables.ordersForTable(ctx.pathId("id")));
        });

        // ---------- admin: payments, settings, users
        router.get("/api/admin/payments", ctx -> {
            ctx.requireStaff();
            ctx.ok(payments.findPayments(ctx.query("status"), ctx.query("method"),
                    "today".equals(ctx.query("range")), ctx.queryInt("limit", 300)));
        });
        router.get("/api/admin/settings", ctx -> {
            ctx.requireAdmin();
            ctx.ok(settings.all());
        });
        router.put("/api/admin/settings", ctx -> {
            ctx.requireAdmin();
            ctx.ok(settings.update(ctx.body()));
        });
        router.get("/api/admin/users", ctx -> {
            ctx.requireAdmin();
            ctx.ok(auth.listUsers(ctx.query("role")));
        });
        router.post("/api/admin/users", ctx -> {
            ctx.requireAdmin();
            ctx.created(auth.createUser(ctx.bodyText("fullName"), ctx.bodyText("email"),
                    ctx.bodyText("phone"), ctx.bodyText("password"), ctx.bodyText("role")));
        });
        router.patch("/api/admin/users/{id}", ctx -> {
            User admin = ctx.requireAdmin();
            ctx.ok(auth.updateAccess(admin, ctx.pathId("id"), ctx.bodyText("role"),
                    ctx.body().path("active").asBoolean(true)));
        });
    }
}
