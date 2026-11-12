# TableBite

TableBite is a Java + Vue restaurant ordering system built around network programming concepts. It combines an HTTP API, a raw TCP server, and a lightweight Java WebSocket server to support table ordering, kitchen order management, and live status updates for customers.

## Overview

The project is designed for a university network programming assignment and demonstrates:

- concurrent TCP client handling
- JDBC-backed PostgreSQL access
- QR-based table routing
- kitchen staff authorization
- live order notifications over WebSockets
- real-time status updates without page refreshes

## Current features

- Customer menu browsing using table QR tokens
- Add-to-cart ordering flow for a table
- Order placement through the Java HTTP API
- PostgreSQL persistence for orders and menu data
- Kitchen dashboard for accepting and updating orders
- Validated order status transitions:
  - PENDING
  - ACCEPTED
  - PREPARING
  - READY
  - SERVED
  - CANCELLED
- Customer tracking page showing live status progression
- WebSocket notifications for:
  - ORDER_CREATED
  - ORDER_STATUS_CHANGED
- TCP server with basic error handling, oversized message rejection, and graceful disconnect handling

## Technology stack

### Backend
- Java 21
- Maven
- PostgreSQL
- JDBC
- Jackson
- Java WebSocket library (`org.java-websocket`)

### Frontend
- Vue 3
- Vite
- Vue Router
- Vanilla JS service layer for HTTP/WebSocket communication

## Network architecture

The app now runs three communication layers at the same time:

1. HTTP server on port 8080
   - serves menu, table lookup, order creation, and order updates
2. TCP server on port 5000
   - handles raw JSON requests like PING, NEW_ORDER, and ORDER_STATUS_UPDATE
3. WebSocket server on port 8081
   - delivers live kitchen and order-status events to browser clients

This is intentionally incremental and keeps the original project structure intact without migrating to Spring Boot or introducing a separate message broker.

## Order workflow

A typical order lifecycle is:

- customer scans a table QR code
- customer adds items to cart
- customer places order
- backend creates new order in PostgreSQL with status `PENDING`
- kitchen dashboard receives `ORDER_CREATED`
- kitchen accepts order → `ACCEPTED`
- kitchen starts prep → `PREPARING`
- kitchen marks ready → `READY`
- customer sees live updates on tracking page
- final serving sets `SERVED`

Invalid transitions are rejected by the backend to keep the workflow consistent.

## Project structure

```text
TableBite/
├── backend/
│   ├── pom.xml
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/tablebite/
│   │   │   │   ├── database/
│   │   │   │   ├── server/
│   │   │   │   └── Main.java
│   │   │   └── resources/
│   │   └── test/java/com/tablebite/
│   └── target/
├── frontend/
│   ├── package.json
│   ├── src/
│   └── vite.config.js
├── README.md
├── run.sh
└── docs/
```

## Startup requirements

Before running the app, make sure:

- Java 21 is installed
- Maven is installed
- Node.js and npm are installed
- PostgreSQL is running locally
- a database named `tablebite_db` exists, or the app is pointed to the correct PostgreSQL instance

## Environment variables

The backend expects:

```bash
DB_USER
DB_PASSWORD
STAFF_ACCESS_CODE
HTTP_PORT
TCP_PORT
WEBSOCKET_PORT
```

The frontend uses:

```bash
VITE_STAFF_ACCESS_CODE
VITE_WS_URL
```

Example:

```bash
export DB_USER=postgres
export DB_PASSWORD=your_password
export STAFF_ACCESS_CODE=test
export HTTP_PORT=8080
export TCP_PORT=5000
export WEBSOCKET_PORT=8081
export VITE_STAFF_ACCESS_CODE=test
```

## Run the project

### 1. Start PostgreSQL

Make sure your local PostgreSQL instance is running on:

- host: `localhost`
- port: `5432`

### 2. Start the backend

From the project root:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q dependency:build-classpath -Dmdep.outputFile=target/runtime-classpath.txt -Dmdep.includeScope=runtime
```

Then run:

```bash
$env:DB_USER='postgres'
$env:DB_PASSWORD='your_password'
$env:STAFF_ACCESS_CODE='test'
$env:HTTP_PORT='8080'
$env:TCP_PORT='5000'
$env:WEBSOCKET_PORT='8081'
java -cp "target/classes;$(Get-Content target/runtime-classpath.txt -Raw)" com.tablebite.Main
```

On Linux/macOS, use:

```bash
export DB_USER=postgres
export DB_PASSWORD=your_password
export STAFF_ACCESS_CODE=test
export HTTP_PORT=8080
export TCP_PORT=5000
export WEBSOCKET_PORT=8081
java -cp "target/classes:$(cat target/runtime-classpath.txt)" com.tablebite.Main
```

### 3. Start the frontend

```bash
cd frontend
npm install
npm run dev -- --host 0.0.0.0
```

Then open the app in a browser:

- customer flow: http://localhost:5173/
- table flow: http://localhost:5173/table/tablebite-demo-t01-8c9d4d1e
- kitchen login: http://localhost:5173/staff-access

## Kitchen access

To access the kitchen dashboard:

1. Open http://localhost:5173/staff-access
2. Enter the staff access code
3. Use the same value configured as `STAFF_ACCESS_CODE`

Example:

```text
test
```

After sign-in, the kitchen dashboard is available at:

- http://localhost:5173/internal/kitchen

## Demo usage

### Customer demo

1. Open a table route such as:
   - http://localhost:5173/table/tablebite-demo-t01-8c9d4d1e
2. Add Chicken Fried Rice to the cart
3. Place the order
4. Open the order tracking page and watch the status updates

### Kitchen demo

1. Open the staff sign-in page
2. Sign in with the configured staff code
3. Open the kitchen dashboard
4. Accept the order, then move it through:
   - ACCEPTED
   - PREPARING
   - READY
5. Customer tracking updates should appear automatically without refreshing

## HTTP endpoints

The backend provides the following routes under `/api`:

```bash
GET /api/menu
GET /api/tables/{token}
GET /api/orders
POST /api/orders
GET /api/orders/{id}
PATCH /api/orders/{id}
```

Example:

```bash
curl http://localhost:8080/api/menu
curl http://localhost:8080/api/tables/tablebite-demo-t01-8c9d4d1e
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"tableToken":"tablebite-demo-t01-8c9d4d1e","items":[{"menuItemId":1,"quantity":1}]}'
```

## TCP protocol

The raw TCP server accepts newline-delimited JSON messages.

Example request:

```json
{
  "type": "PING"
}
```

Example response:

```json
{
  "type": "PONG",
  "message": "TableBite server is running"
}
```

## WebSocket events

The WebSocket server emits:

- `ORDER_CREATED`
- `ORDER_STATUS_CHANGED`
- `PONG`
- `SUBSCRIBED`
- `ERROR`

Kitchen clients subscribe to kitchen notifications, while customer clients subscribe to the order-specific tracking channel.

## Current status

This project is an incremental network-programming milestone rather than a finished production restaurant platform. It demonstrates working HTTP, TCP, and WebSocket communication patterns while preserving the existing multi-server design and minimal scope of the assignment.

## License

This project is for educational and academic use.
