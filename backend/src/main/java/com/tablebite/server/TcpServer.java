package com.tablebite.server;

import com.tablebite.service.AuthenticationService;
import com.tablebite.util.LoggerUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Multithreaded TCP server for the newline-delimited JSON protocol.
 *
 * <p>Threading model: one acceptor thread calls {@link ServerSocket#accept()} and hands
 * each connection to a bounded {@link ThreadPoolExecutor} ({@code maxClients} worker
 * threads, at most {@code maxQueuedClients} connections waiting). When both are full the
 * connection is answered with a {@code SERVER_BUSY} error and closed instead of piling up.
 *
 * <p>Shutdown: {@link #stop()} closes the listening socket, lets each handler finish its
 * current request and send a {@code SERVER_SHUTDOWN} notice, then force-closes whatever
 * is still open after the grace period.
 */
public class TcpServer {
    private static final Logger LOG = LoggerUtil.get(TcpServer.class);

    /** Network and resource limits. {@link #fromEnvironment(int)} reads the TCP_* variables. */
    public record Settings(String host, int port, int maxClients, int maxQueuedClients,
                           int idleTimeoutMillis, int pollIntervalMillis, int maxMessageBytes,
                           long maxDiscardBytes, int shutdownGraceMillis) {

        public static final String DEFAULT_HOST = "127.0.0.1";
        public static final int DEFAULT_MAX_MESSAGE_BYTES = 64 * 1024;

        public Settings {
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("TCP host is required");
            }
            if (port < 0 || port > 65_535 || maxClients < 1 || maxQueuedClients < 0
                    || idleTimeoutMillis < 1 || pollIntervalMillis < 1 || maxMessageBytes < 1
                    || maxDiscardBytes < maxMessageBytes || shutdownGraceMillis < 0) {
                throw new IllegalArgumentException("Invalid TCP server settings");
            }
        }

        public static Settings defaults(int port) {
            return new Settings(DEFAULT_HOST, port, 32, 16, 30_000, 500,
                    DEFAULT_MAX_MESSAGE_BYTES, 16L * DEFAULT_MAX_MESSAGE_BYTES, 5_000);
        }

        /**
         * TCP_HOST (default 127.0.0.1), TCP_MAX_CLIENTS (32), TCP_MAX_QUEUE (16),
         * TCP_IDLE_TIMEOUT_SECONDS (30).
         */
        public static Settings fromEnvironment(int port) {
            Settings defaults = defaults(port);
            String host = System.getenv().getOrDefault("TCP_HOST", DEFAULT_HOST).trim();
            int idleSeconds = envInt("TCP_IDLE_TIMEOUT_SECONDS", defaults.idleTimeoutMillis() / 1000, 1, 3_600);
            return new Settings(host.isEmpty() ? DEFAULT_HOST : host, port,
                    envInt("TCP_MAX_CLIENTS", defaults.maxClients(), 1, 1_000),
                    envInt("TCP_MAX_QUEUE", defaults.maxQueuedClients(), 0, 10_000),
                    idleSeconds * 1000, Math.min(defaults.pollIntervalMillis(), idleSeconds * 1000),
                    defaults.maxMessageBytes(), defaults.maxDiscardBytes(),
                    defaults.shutdownGraceMillis());
        }

        private static int envInt(String name, int fallback, int min, int max) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                return fallback;
            }
            try {
                int parsed = Integer.parseInt(value.trim());
                if (parsed >= min && parsed <= max) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // fall through to the warning
            }
            LOG.warning(name + "=" + value + " is invalid (expected " + min + "-" + max
                    + "); using " + fallback);
            return fallback;
        }
    }

    private final Settings settings;
    private final ClientHandler.Services services;
    private final ThreadPoolExecutor executor;
    private final Set<ClientHandler> handlers = ConcurrentHashMap.newKeySet();
    private final AtomicLong connectionIds = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private volatile ServerSocket serverSocket;
    private volatile Thread acceptor;

    /** Legacy convenience constructor: default settings on the given port. */
    public TcpServer(int port) {
        this(Settings.defaults(port), new AuthenticationService());
    }

    public TcpServer(Settings settings, AuthenticationService auth) {
        this(settings, ClientHandler.Services.create(auth));
    }

    TcpServer(Settings settings, ClientHandler.Services services) {
        this.settings = settings;
        this.services = services;
        BlockingQueue<Runnable> queue = settings.maxQueuedClients() == 0
                ? new SynchronousQueue<>() : new ArrayBlockingQueue<>(settings.maxQueuedClients());
        this.executor = new ThreadPoolExecutor(settings.maxClients(), settings.maxClients(),
                60, TimeUnit.SECONDS, queue, new WorkerThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
        this.executor.allowCoreThreadTimeOut(true);
    }

    /** Binds the listening socket and starts the acceptor thread. Returns immediately. */
    public synchronized void start() throws IOException {
        if (stopped.get()) {
            throw new IllegalStateException("TCP server has been stopped");
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        InetAddress address = InetAddress.getByName(settings.host());
        ServerSocket socket = new ServerSocket();
        try {
            socket.bind(new InetSocketAddress(address, settings.port()), 50);
        } catch (IOException exception) {
            socket.close();
            running.set(false);
            throw exception;
        }
        serverSocket = socket;
        if (!address.isLoopbackAddress()) {
            LOG.warning("TCP server is listening on non-loopback address " + address.getHostAddress()
                    + ". Private commands require AUTH, but traffic is unencrypted plain text;"
                    + " expose it only on a trusted network.");
        }
        LOG.info("TableBite TCP server listening on " + socket.getLocalSocketAddress()
                + " (workers=" + settings.maxClients() + ", queue=" + settings.maxQueuedClients()
                + ", idle timeout=" + settings.idleTimeoutMillis() + " ms, max message="
                + settings.maxMessageBytes() + " bytes)");
        Thread thread = new Thread(this::acceptLoop, "tablebite-tcp-acceptor");
        thread.setDaemon(true);
        acceptor = thread;
        thread.start();
    }

    /** The bound port (useful when started on port 0), or -1 before start. */
    public int getLocalPort() {
        ServerSocket socket = serverSocket;
        return socket == null ? -1 : socket.getLocalPort();
    }

    public boolean isRunning() {
        return running.get();
    }

    /** Connections currently being served or waiting for a worker thread. */
    public int activeConnectionCount() {
        return handlers.size();
    }

    private void acceptLoop() {
        ServerSocket listener = serverSocket;
        while (running.get()) {
            Socket socket;
            try {
                socket = listener.accept();
            } catch (IOException exception) {
                if (!running.get() || listener.isClosed()) {
                    break;
                }
                // e.g. too many open files: log and back off briefly instead of spinning.
                LOG.warning("TCP accept failed: " + exception.getMessage());
                pause(100);
                continue;
            }
            dispatch(socket);
        }
        LOG.info("TCP acceptor stopped");
    }

    private void dispatch(Socket socket) {
        long id = connectionIds.incrementAndGet();
        ClientHandler handler;
        try {
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            handler = new ClientHandler(socket, id, services, settings, handlers::remove);
        } catch (IOException | RuntimeException exception) {
            LOG.warning("TCP #" + id + " could not be set up: " + exception.getMessage());
            closeQuietly(socket);
            return;
        }
        handlers.add(handler);
        try {
            executor.execute(handler);
            LOG.info("TCP #" + id + " connected from " + socket.getRemoteSocketAddress()
                    + " (active=" + executor.getActiveCount() + "/" + settings.maxClients()
                    + ", waiting=" + executor.getQueue().size() + ")");
        } catch (RejectedExecutionException exception) {
            handlers.remove(handler);
            reject(socket, id, running.get() ? "SERVER_BUSY" : "SERVER_SHUTDOWN");
        }
    }

    /** Tells an over-limit client why it is being dropped, without using a worker thread. */
    private void reject(Socket socket, long id, String code) {
        LOG.warning("TCP #" + id + " rejected from " + socket.getRemoteSocketAddress() + ": "
                + code + " (" + settings.maxClients() + " workers busy, "
                + settings.maxQueuedClients() + " waiting)");
        try {
            socket.setSoLinger(true, 1);
            OutputStream out = socket.getOutputStream();
            out.write((ClientHandler.error(code, "SERVER_BUSY".equals(code)
                    ? "Server is at capacity, please retry shortly"
                    : "Server is shutting down") + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            socket.shutdownOutput();
        } catch (IOException ignored) {
            // the client is gone already
        } finally {
            closeQuietly(socket);
        }
    }

    /**
     * Graceful shutdown: stop accepting, let in-flight requests finish, notify idle clients,
     * then force-close any connection still open after the grace period. Idempotent.
     */
    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        running.set(false);
        LOG.info("Stopping TCP server (" + handlers.size() + " open connections)");
        closeQuietly(serverSocket);
        executor.shutdown();
        handlers.forEach(ClientHandler::requestStop);
        try {
            if (!executor.awaitTermination(settings.shutdownGraceMillis(), TimeUnit.MILLISECONDS)) {
                LOG.warning("TCP shutdown grace period expired; closing "
                        + handlers.size() + " connections");
                handlers.forEach(ClientHandler::forceClose);
                executor.shutdownNow().forEach(task -> {
                    if (task instanceof ClientHandler handler) {
                        handler.forceClose();
                    }
                });
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    LOG.warning("Some TCP worker threads did not terminate");
                }
            }
            Thread thread = acceptor;
            if (thread != null) {
                thread.join(1_000);
            }
        } catch (InterruptedException exception) {
            handlers.forEach(ClientHandler::forceClose);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOG.info("TCP server stopped");
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception exception) {
            LOG.log(Level.FINE, "Close failed", exception);
        }
    }

    private static final class WorkerThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "tablebite-tcp-worker-" + counter.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, error) ->
                    LOG.log(Level.SEVERE, "Uncaught error in " + t.getName(), error));
            return thread;
        }
    }
}
