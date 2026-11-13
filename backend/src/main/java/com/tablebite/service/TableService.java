package com.tablebite.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tablebite.database.OrderRepository;
import com.tablebite.database.TableRepository;
import com.tablebite.model.Order;
import com.tablebite.model.RestaurantTable;
import com.tablebite.util.SecurityUtil;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Restaurant tables and their QR tokens. */
public class TableService {
    private static final Set<String> STATUSES = Set.of("AVAILABLE", "OCCUPIED", "RESERVED", "OUT_OF_SERVICE");
    private static final Pattern NUMBER = Pattern.compile("^[A-Za-z0-9 -]{1,12}$");

    private final TableRepository tables;
    private final OrderRepository orders;

    public TableService() {
        this(new TableRepository(), new OrderRepository());
    }

    public TableService(TableRepository tables, OrderRepository orders) {
        this.tables = tables;
        this.orders = orders;
    }

    /** New unguessable token: "tb-" + 128 random bits. */
    public static String newToken() {
        return "tb-" + SecurityUtil.randomHex(16);
    }

    public RestaurantTable resolveToken(String token) throws SQLException {
        RestaurantTable table = tables.findByQrToken(token);
        if (table == null) {
            throw new ServiceException(404, "TABLE_NOT_FOUND",
                    "This QR code isn't linked to a table. Please scan the code on your table again or ask our staff.");
        }
        return table;
    }

    public RestaurantTable resolveNumber(String number) throws SQLException {
        RestaurantTable table = tables.findByNumber(number);
        if (table == null) {
            throw new ServiceException(404, "TABLE_NOT_FOUND",
                    "We couldn't find table \"" + number + "\". Check the number printed on your table.");
        }
        return table;
    }

    public List<RestaurantTable> list() throws SQLException {
        return tables.findAll();
    }

    public RestaurantTable create(JsonNode body) throws SQLException {
        Input input = parse(body);
        if (tables.numberExists(input.number(), null)) {
            throw ServiceException.conflict("Table " + input.number() + " already exists");
        }
        return tables.create(input.number(), newToken(), input.seats(), input.status(), input.location());
    }

    public RestaurantTable update(long id, JsonNode body) throws SQLException {
        Input input = parse(body);
        if (tables.numberExists(input.number(), id)) {
            throw ServiceException.conflict("Table " + input.number() + " already exists");
        }
        RestaurantTable updated = tables.update(id, input.number(), input.seats(), input.status(), input.location());
        if (updated == null) {
            throw ServiceException.notFound("Table");
        }
        return updated;
    }

    /** Issues a new QR token; previously printed codes for this table stop working. */
    public RestaurantTable regenerateToken(long id) throws SQLException {
        RestaurantTable updated = tables.updateToken(id, newToken());
        if (updated == null) {
            throw ServiceException.notFound("Table");
        }
        return updated;
    }

    public void delete(long id) throws SQLException {
        RestaurantTable table = tables.findById(id);
        if (table == null) {
            throw ServiceException.notFound("Table");
        }
        if (table.activeOrders() > 0) {
            throw ServiceException.conflict("Table " + table.tableNumber()
                    + " still has active orders. Complete them before deleting the table.");
        }
        tables.delete(id, "archived-" + SecurityUtil.randomHex(16));
    }

    public List<Order> ordersForTable(long id) throws SQLException {
        if (tables.findById(id) == null) {
            throw ServiceException.notFound("Table");
        }
        return orders.findOrders(new OrderRepository.OrderFilter(null, null, null, id, null,
                false, false, null, 50), true).stream().map(Order::withoutToken).toList();
    }

    private record Input(String number, int seats, String status, String location) {
    }

    private Input parse(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("Request body must be a JSON object");
        }
        String number = Validation.requireText(MenuService.text(body, "tableNumber"), "Table number", 1, 12)
                .toUpperCase();
        if (!NUMBER.matcher(number).matches()) {
            throw new IllegalArgumentException("Table number can contain letters, numbers, spaces and dashes");
        }
        int seats = Validation.range(body.hasNonNull("seats") ? body.get("seats").asInt() : null,
                "Seats", 1, 50, 4);
        String status = MenuService.text(body, "status");
        status = status == null || status.isBlank() ? "AVAILABLE" : status.trim().toUpperCase();
        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException("Unsupported table status");
        }
        String location = Validation.optionalText(MenuService.text(body, "location"), "Location", 60);
        return new Input(number, seats, status, location);
    }
}
