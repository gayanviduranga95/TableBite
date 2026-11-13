# TableBite – System Architecture

## Overview

```
 Phone / browser (Vue 3 SPA)                     Kitchen screen / staff PCs
        │  HTTP  /api/*   ─────────┐                     │
        │  WS    /ws      ───────┐ │   Vite dev proxy     │
        ▼                        ▼ ▼   (one origin)       ▼
 ┌───────────────────────────────────────────────────────────────┐
 │                    Java 21 backend (Main)                     │
 │                                                               │
 │  HttpApiServer :8080   LiveWebSocketServer :8081  TcpServer :5000
 │  (JDK HttpServer,      (Java-WebSocket,           (raw sockets,
 │   16-thread pool)       push notifications)        10-thread pool)
 │        │                        ▲                       │      │
 │        ▼                        │                       ▼      │
 │  Router → CustomerRoutes / StaffRoutes             ClientHandler│
 │        │                        │                       │      │
 │        ▼                        │                       │      │
 │  Services: Authentication · Order · Menu · Table · Settings    │
 │            NotificationService ─┘                       │      │
 │        │                                                │      │
 │        ▼                                                ▼      │
 │  Repositories (JDBC, prepared statements, transactions)        │
 └────────┬───────────────────────────────────────────────────────┘
          ▼
     PostgreSQL (tablebite_db) – schema managed by SchemaMigrator
```

## Layers

| Layer | Package | Responsibility |
|---|---|---|
| Transport | `server`, `server.api` | HTTP routing, request parsing, auth extraction, JSON responses, CORS/security headers, static `/uploads`, WebSocket channels, TCP protocol |
| Services | `service` | Business rules: pricing inputs, role permissions, status transitions, cancellation rules, payments, validation, notifications |
| Persistence | `database` | JDBC repositories, transactions, row locks, migrations |
| Model | `model` | Immutable records/enums shared by all layers (`Order`, `MenuItem`, `OrderStatus`, `UserRole`, …) |
| Payments | `payment` | `PaymentGateway` integration point (no gateway bundled) |
| Utilities | `util` | Logging, JSON mapper, password hashing & secure tokens |

## Concurrency (OS / network programming concepts)

* **Thread pools** – the HTTP server uses a fixed pool of 16 worker threads, the TCP
  server a pool of 10 (`ExecutorService`). Each TCP connection is handled by a
  `ClientHandler` runnable with a 30 s socket timeout and a 64 KB message limit.
* **WebSocket server** – runs its own selector thread; subscriber sets are
  `ConcurrentHashMap` key-sets so publishes from HTTP threads are thread-safe.
* **Database concurrency** – order creation locks the dishes (`SELECT … FOR SHARE`),
  status changes lock the order row (`SELECT … FOR UPDATE`), payments lock the order
  row so it cannot be paid twice. A unique partial index on `idempotency_key` plus
  retry-safe client keys prevents duplicate orders from double taps or retries.
* **Rate limiting** – failed logins are tracked per e-mail + client in a
  `ConcurrentHashMap<String, Deque<Instant>>` (8 failures / 10 minutes).

## Security

* Passwords: PBKDF2-HMAC-SHA256, 210 000 iterations, 16-byte random salt.
* Sessions: 256-bit random bearer tokens; only their SHA-256 hash is stored
  (`user_sessions`), expired sessions are purged on start-up, logout revokes.
* Role-based access: `CUSTOMER`, `ADMIN`, `KITCHEN`, `WAITER` (+ legacy `STAFF` for the
  optional shared code). Checked on every endpoint and per status transition.
* Orders are private: customers need ownership or the order's secret tracking token;
  ids cannot be enumerated (same 404 for "missing" and "not yours").
* Table QR codes contain an unguessable token (`tb-` + 128 random bits) which can be
  regenerated; numeric table ids are no longer accepted.
* All SQL uses prepared statements; uploads are validated by magic bytes, stored under
  random names, served with `nosniff`; path traversal is rejected.
* No credentials in code – everything comes from environment variables.

## Frontend

Vue 3 + Vite + Vue Router + Tailwind CSS 4 + lucide icons.

* `views/customer` – home, menu, dish details, QR scan, table entry, cart/checkout,
  live tracking, order history, auth & profile.
* `views/admin`, `views/kitchen`, `views/waiter` – staff console, kitchen display, floor.
* `services/api.js` – fetch wrapper (bearer token, error mapping);
  `services/socketService.js` – auto-reconnecting WebSocket channels.
* `stores/` – reactive auth, settings and a persisted cart / table context.
* QR: `qrcode` generates codes and printable cards; `jsQR` (or the native
  `BarcodeDetector`) decodes camera frames or photos.
