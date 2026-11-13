package com.tablebite.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.tablebite.server.TcpTestSupport.Client;
import com.tablebite.server.TcpTestSupport.FakeMenu;
import com.tablebite.server.TcpTestSupport.FakeOrders;
import com.tablebite.service.AuthenticationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static com.tablebite.server.TcpServerTest.assertError;
import static com.tablebite.server.TcpTestSupport.CUSTOMER_TOKEN;
import static com.tablebite.server.TcpTestSupport.STAFF_TOKEN;
import static com.tablebite.server.TcpTestSupport.TABLE_QR;
import static com.tablebite.server.TcpTestSupport.TRACKING_TOKEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Protocol commands and access control of the TCP {@link ClientHandler}. */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ClientHandlerTest {
    private final FakeOrders orders = new FakeOrders();
    private TcpServer server;
    private Client client;

    @BeforeEach
    void start() throws Exception {
        server = TcpTestSupport.startServer(TcpTestSupport.settings(4, 4, 10_000, 1_000),
                TcpTestSupport.services(orders, new FakeMenu()));
        client = new Client(server.getLocalPort());
    }

    @AfterEach
    void stop() throws Exception {
        client.close();
        server.stop();
    }

    // ------------------------------------------------------------------ public commands

    @Test
    void publicCommandsWorkWithoutAuthentication() throws Exception {
        assertEquals("PONG", client.request("{\"type\":\"PING\"}").get("type").asText());
        assertTrue(client.request("{\"type\":\"GET_MENU\"}").isArray());
    }

    // ------------------------------------------------------------------ anonymous access is denied

    @Test
    void anonymousClientCannotListOrders() throws Exception {
        assertError(client.request("{\"type\":\"GET_ORDERS\"}"), "AUTH_REQUIRED");
        assertFalse(orders.calls.contains("findAll"));
    }

    @Test
    void anonymousClientCannotChangeOrderStatus() throws Exception {
        assertError(client.request("{\"type\":\"ORDER_STATUS_UPDATE\",\"orderId\":1,\"status\":\"CONFIRMED\"}"),
                "AUTH_REQUIRED");
        assertEquals("PENDING", orders.status.get());
        assertTrue(orders.calls.stream().noneMatch(call -> call.startsWith("changeStatus")));
    }

    @Test
    void clientSuppliedRoleAndUserIdAreIgnored() throws Exception {
        assertError(client.request("{\"type\":\"GET_ORDERS\",\"role\":\"ADMIN\",\"userId\":1}"),
                "AUTH_REQUIRED");
        assertError(client.request("{\"type\":\"ORDER_STATUS_UPDATE\",\"orderId\":1,"
                + "\"status\":\"CONFIRMED\",\"role\":\"KITCHEN\",\"staff\":true}"), "AUTH_REQUIRED");
        assertEquals("PENDING", orders.status.get());
    }

    @Test
    void anonymousOrderLookupNeedsTheTrackingToken() throws Exception {
        assertError(client.request("{\"type\":\"GET_ORDER\",\"orderId\":1}"), "AUTH_REQUIRED");
        // A wrong token and a missing order look identical, so ids cannot be probed.
        assertError(client.request("{\"type\":\"GET_ORDER\",\"orderId\":1,\"trackingToken\":\"guess\"}"),
                "ORDER_NOT_FOUND");
        assertError(client.request("{\"type\":\"GET_ORDER\",\"orderId\":999,\"trackingToken\":\"guess\"}"),
                "ORDER_NOT_FOUND");

        JsonNode order = client.request("{\"type\":\"GET_ORDER\",\"orderId\":1,\"trackingToken\":\""
                + TRACKING_TOKEN + "\"}");
        assertEquals(1, order.get("id").asLong());
        assertTrue(order.get("trackingToken").isNull(), "secret token must not be echoed");

        JsonNode status = client.request("{\"type\":\"GET_ORDER_STATUS\",\"orderId\":1,\"trackingToken\":\""
                + TRACKING_TOKEN + "\"}");
        assertEquals("ORDER_STATUS", status.get("type").asText());
        assertEquals("PENDING", status.get("status").asText());
    }

    @Test
    void anonymousOrderByRawTableIdIsRejected() throws Exception {
        assertError(client.request("{\"type\":\"NEW_ORDER\",\"tableId\":1,"
                + "\"items\":[{\"menuItemId\":1,\"quantity\":2}]}"), "AUTH_REQUIRED");
        assertTrue(orders.calls.isEmpty());
    }

    @Test
    void anonymousOrderWithTableQrTokenIsPlacedAndReturnsTrackingToken() throws Exception {
        JsonNode created = client.request("{\"type\":\"NEW_ORDER\",\"tableToken\":\"" + TABLE_QR
                + "\",\"items\":[{\"menuItemId\":1,\"quantity\":2}]}");
        assertEquals("ORDER_CREATED", created.get("type").asText());
        assertEquals(43, created.get("orderId").asLong());
        assertEquals(TRACKING_TOKEN, created.get("trackingToken").asText());
        assertTrue(orders.calls.contains("create:1:null"));

        assertError(client.request("{\"type\":\"NEW_ORDER\",\"tableToken\":\"wrong\","
                + "\"items\":[{\"menuItemId\":1,\"quantity\":2}]}"), "TABLE_NOT_FOUND");
    }

    // ------------------------------------------------------------------ authentication

    @Test
    void invalidCredentialsAreRejectedAndRepeatedFailuresCloseTheConnection() throws Exception {
        assertError(client.request("{\"type\":\"AUTH\"}"), "VALIDATION_ERROR");
        for (int i = 1; i <= ClientHandler.MAX_AUTH_FAILURES; i++) {
            assertError(client.request("{\"type\":\"AUTH\",\"token\":\"wrong-" + i + "\"}"), "AUTH_FAILED");
        }
        assertTrue(client.isClosedByServer());
    }

    @Test
    void staffSessionTokenUnlocksStaffCommands() throws Exception {
        JsonNode auth = client.request("{\"type\":\"AUTH\",\"token\":\"" + STAFF_TOKEN + "\"}");
        assertEquals("AUTH_OK", auth.get("type").asText());
        assertEquals("KITCHEN", auth.get("role").asText());

        JsonNode list = client.request("{\"type\":\"GET_ORDERS\"}");
        assertTrue(list.isArray());
        assertTrue(list.get(0).get("trackingToken").isNull());

        JsonNode updated = client.request("{\"type\":\"ORDER_STATUS_UPDATE\",\"orderId\":1,\"status\":\"CONFIRMED\"}");
        assertEquals("ORDER_STATUS_UPDATED", updated.get("type").asText());
        assertEquals("CONFIRMED", updated.get("status").asText());
        assertEquals("CONFIRMED", orders.status.get());

        // OrderService role rules apply: the kitchen cannot mark an order SERVED.
        assertError(client.request("{\"type\":\"ORDER_STATUS_UPDATE\",\"orderId\":1,\"status\":\"SERVED\"}"),
                "FORBIDDEN");
        // ...and invalid transitions are refused.
        assertError(client.request("{\"type\":\"ORDER_STATUS_UPDATE\",\"orderId\":1,\"status\":\"PENDING\"}"),
                "FORBIDDEN");
        assertError(client.request("{\"type\":\"ORDER_STATUS_UPDATE\",\"orderId\":1,\"status\":\"COOKED\"}"),
                "VALIDATION_ERROR");
        assertEquals("CONFIRMED", orders.status.get());
    }

    @Test
    void kitchenAccessCodeAuthenticatesAsKitchen() throws Exception {
        JsonNode auth = client.request("{\"type\":\"AUTH\",\"accessCode\":\""
                + AuthenticationService.kitchenCode() + "\"}");
        assertEquals("AUTH_OK", auth.get("type").asText());
        assertEquals("KITCHEN", auth.get("role").asText());
        assertTrue(client.request("{\"type\":\"GET_ORDERS\"}").isArray());
    }

    @Test
    void customerSessionCannotUseStaffCommands() throws Exception {
        assertEquals("AUTH_OK", client.request("{\"type\":\"AUTH\",\"token\":\"" + CUSTOMER_TOKEN + "\"}")
                .get("type").asText());
        assertError(client.request("{\"type\":\"GET_ORDERS\"}"), "FORBIDDEN");
        assertError(client.request("{\"type\":\"ORDER_STATUS_UPDATE\",\"orderId\":1,\"status\":\"CANCELLED\"}"),
                "FORBIDDEN");
        assertEquals("PENDING", orders.status.get());
        // ...but may read their own order without the tracking token.
        assertEquals(1, client.request("{\"type\":\"GET_ORDER\",\"orderId\":1}").get("id").asLong());
    }

    @Test
    void failedReAuthenticationDropsPreviousIdentity() throws Exception {
        client.request("{\"type\":\"AUTH\",\"token\":\"" + STAFF_TOKEN + "\"}");
        assertError(client.request("{\"type\":\"AUTH\",\"token\":\"bogus\"}"), "AUTH_FAILED");
        assertError(client.request("{\"type\":\"GET_ORDERS\"}"), "AUTH_REQUIRED");
    }

    // ------------------------------------------------------------------ legacy order flow (staff)

    @Test
    void staffCreatesOrderByTableIdAndReturnsGeneratedId() throws Exception {
        client.request("{\"type\":\"AUTH\",\"token\":\"" + STAFF_TOKEN + "\"}");
        JsonNode response = client.request("{\"type\":\"NEW_ORDER\",\"tableId\":1,"
                + "\"items\":[{\"menuItemId\":1,\"quantity\":2}]}");
        assertEquals("ORDER_CREATED", response.get("type").asText());
        assertTrue(response.get("success").asBoolean());
        assertEquals(42L, response.get("orderId").asLong());
        assertEquals("PENDING", response.get("status").asText());
        assertTrue(orders.calls.contains("createOrder:1:[OrderItemInput[menuItemId=1, quantity=2, notes=null]]"));
    }

    @Test
    void rejectsInvalidOrderAndKeepsConnectionUsable() throws Exception {
        client.request("{\"type\":\"AUTH\",\"token\":\"" + STAFF_TOKEN + "\"}");
        assertError(client.request("{\"type\":\"NEW_ORDER\",\"tableId\":0,\"items\":[]}"), "VALIDATION_ERROR");
        assertError(client.request("{\"type\":\"NEW_ORDER\",\"tableId\":1,\"items\":[{\"menuItemId\":\"x\",\"quantity\":1}]}"),
                "VALIDATION_ERROR");
        assertError(client.request("{\"type\":\"NEW_ORDER\",\"tableId\":1,\"items\":[5]}"), "VALIDATION_ERROR");
        assertTrue(orders.calls.isEmpty());
        assertEquals("PONG", client.request("{\"type\":\"PING\"}").get("type").asText());
    }
}
