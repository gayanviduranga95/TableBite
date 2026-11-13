package com.tablebite.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tablebite.database.MenuRepository;
import com.tablebite.database.OrderRepository;
import com.tablebite.database.PaymentRepository;
import com.tablebite.database.SettingsRepository;
import com.tablebite.database.TableRepository;
import com.tablebite.model.MenuItem;
import com.tablebite.model.Order;
import com.tablebite.model.OrderStatus;
import com.tablebite.model.RestaurantTable;
import com.tablebite.model.User;
import com.tablebite.model.UserRole;
import com.tablebite.service.AuthenticationService;
import com.tablebite.service.OrderService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** In-memory services and a line-oriented client for TCP tests (no database needed). */
final class TcpTestSupport {
    static final ObjectMapper MAPPER = new ObjectMapper();
    static final String STAFF_TOKEN = "staff-session-token";
    static final String CUSTOMER_TOKEN = "customer-session-token";
    static final String TRACKING_TOKEN = "secret-tracking-token";
    static final String TABLE_QR = "table-qr-token";
    static final User KITCHEN_USER = new User(7, "Kitchen Cook", "k@x", null, UserRole.KITCHEN, true, null, null);
    static final User CUSTOMER = new User(9, "Customer", "c@x", null, UserRole.CUSTOMER, true, null, null);

    private TcpTestSupport() {
    }

    static TcpServer.Settings settings(int maxClients, int maxQueue, int idleMillis, int graceMillis) {
        return new TcpServer.Settings("127.0.0.1", 0, maxClients, maxQueue, idleMillis, 25,
                64 * 1024, 4L * 64 * 1024, graceMillis);
    }

    static Order order(long id, String status, String token) {
        return new Order(id, "TB-" + id, 1L, "T01", "DINE_IN", status, CUSTOMER.id(), null, null,
                null, null, null, null, null, "PAY_AT_COUNTER", "UNPAID", null, token,
                null, null, 0, List.of(), List.of());
    }

    /** A fake order store that records every call it receives. */
    static final class FakeOrders extends OrderRepository {
        final AtomicReference<String> status = new AtomicReference<>("PENDING");
        final List<String> calls = new CopyOnWriteArrayList<>();

        @Override
        public long createOrder(long tableId, List<OrderItemInput> items) {
            calls.add("createOrder:" + tableId + ":" + items);
            return 42L;
        }

        @Override
        public CreateResult create(NewOrder order) {
            calls.add("create:" + order.tableId() + ":" + order.userId());
            return new CreateResult(43L, false);
        }

        @Override
        public List<Order> findAll() {
            calls.add("findAll");
            return List.of(order(1, status.get(), TRACKING_TOKEN));
        }

        @Override
        public Order findById(long orderId) {
            calls.add("findById:" + orderId);
            return orderId == 1 || orderId == 43 ? order(orderId, status.get(), TRACKING_TOKEN) : null;
        }

        @Override
        public StatusChangeResult changeStatus(long orderId, OrderStatus target, Long actor,
                                               String note, Consumer<OrderStatus> guard) {
            calls.add("changeStatus:" + orderId + ":" + target);
            OrderStatus previous = OrderStatus.parse(status.get());
            guard.accept(previous);
            if (!previous.canTransitionTo(target)) {
                throw new IllegalArgumentException("Cannot change order from " + previous + " to " + target);
            }
            status.set(target.name());
            return new StatusChangeResult(previous, target);
        }
    }

    static final class FakeMenu extends MenuRepository {
        volatile long delayMillis;

        @Override
        public List<MenuItem> findAvailable() {
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted", exception);
                }
            }
            return List.of();
        }
    }

    /** Resolves the two fixed test tokens; the kitchen access code uses the real check. */
    static final class FakeAuth extends AuthenticationService {
        FakeAuth() {
            super(null);
        }

        @Override
        public User authenticate(String token) {
            if (STAFF_TOKEN.equals(token)) {
                return KITCHEN_USER;
            }
            return CUSTOMER_TOKEN.equals(token) ? CUSTOMER : null;
        }
    }

    static ClientHandler.Services services(FakeOrders orders, FakeMenu menu) {
        TableRepository tables = new TableRepository() {
            @Override
            public RestaurantTable findByQrToken(String token) {
                return TABLE_QR.equals(token)
                        ? new RestaurantTable(1, "T01", TABLE_QR, 4, "AVAILABLE", null, 0, null) : null;
            }
        };
        SettingsRepository settings = new SettingsRepository() {
            @Override
            public Map<String, String> findAll() {
                return Map.of();
            }
        };
        return new ClientHandler.Services(orders, menu,
                new OrderService(orders, tables, settings, new PaymentRepository()), new FakeAuth());
    }

    static TcpServer startServer(TcpServer.Settings settings, ClientHandler.Services services)
            throws IOException {
        TcpServer server = new TcpServer(settings, services);
        server.start();
        return server;
    }

    /** Minimal blocking client: writes lines, reads JSON lines, 5 s read timeout. */
    static final class Client implements AutoCloseable {
        final Socket socket;
        private final OutputStream out;
        private final BufferedReader in;

        Client(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(5_000);
            out = socket.getOutputStream();
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        }

        void sendRaw(String data) throws IOException {
            out.write(data.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        void send(String line) throws IOException {
            sendRaw(line + "\n");
        }

        JsonNode read() throws IOException {
            String line = in.readLine();
            if (line == null) {
                throw new IOException("connection closed by server");
            }
            return MAPPER.readTree(line);
        }

        JsonNode request(String line) throws IOException {
            send(line);
            return read();
        }

        /** True once the server has closed the connection (EOF or reset). */
        boolean isClosedByServer() {
            try {
                return in.readLine() == null;
            } catch (IOException exception) {
                return true;
            }
        }

        static List<Client> open(int port, int count) throws IOException {
            List<Client> clients = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                clients.add(new Client(port));
            }
            return clients;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
