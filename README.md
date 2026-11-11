# TableBite 🍽️

**A Real-Time Network-Based QR Restaurant Ordering and Kitchen Coordination System**

## Overview

TableBite is a restaurant ordering system that allows customers to scan a QR code at their table, browse the menu, place orders, and track order progress in real time. Orders are transmitted to the kitchen, where staff can manage preparation and update their status. Waiters and administrators can also monitor orders and coordinate restaurant operations.

The project focuses on network programming, real-time communication, concurrency, and client-server architecture.

## Main Features

- **QR-Based Ordering:** Customers access the restaurant menu using a table QR code.
- **Digital Menu:** Browse food items and prices.
- **Shopping Cart:** Select items and submit orders.
- **Kitchen Dashboard:** Receive orders and update preparation status.
- **Real-Time Order Tracking:** View order status updates.
- **Waiter Dashboard:** Monitor orders and serving requests.
- **Admin Dashboard:** Manage menu items and monitor restaurant activity.
- **Concurrent Clients:** Handle multiple customer and staff connections.

## Technology Stack

### Frontend
- Vue 3
- Vite
- Tailwind CSS
- Vue Router
- Lucide Vue icons

### Backend
- Java 21
- TCP Sockets
- Multithreading and concurrency
- Maven
- Jackson JSON library

### Database
- MySQL
- JDBC

### Version Control
- Git and GitHub

## Project Structure

```text
TableBite/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/tablebite/
│   │   │   └── resources/
│   │   └── test/
│   └── pom.xml
├── frontend/
│   ├── public/
│   ├── src/
│   ├── package.json
│   └── vite.config.js
├── docs/
├── scripts/
└── README.md
```

## System Architecture

1. A customer scans the QR code assigned to their table.
2. The frontend displays the menu and allows the customer to place an order.
3. The backend receives and processes the order.
4. The TCP server manages client connections and distributes order information.
5. The kitchen dashboard receives new orders and updates their status.
6. Status changes are communicated to the relevant clients in real time.

The browser frontend will communicate with the Java backend through a suitable bridge, such as WebSockets, while TCP sockets remain central to the backend networking implementation.

## Getting Started

### Prerequisites

Install the following software:

- Java Development Kit (JDK) 21
- Apache Maven
- Node.js and npm
- MySQL Server
- Git

### Backend Setup

Open a terminal in the backend directory:

```bash
cd backend
mvn clean compile
mvn exec:java -Dexec.mainClass=com.tablebite.Main
```

The backend exposes the browser HTTP API on port `8080` and keeps the TCP
server on port `5000`. Set the following environment variables before startup:

- `DB_USER` - PostgreSQL username
- `DB_PASSWORD` - PostgreSQL password
- `HTTP_PORT` - optional HTTP port override (defaults to `8080`)
- `STAFF_ACCESS_CODE` - required code for internal kitchen, orders, and admin API access

The database URL is `jdbc:postgresql://localhost:5432/tablebite_db`.
Database migrations must already be applied. Do not put credentials in source
files or commit them.

If `qr_attendance_app` exists but receives `permission denied` errors, connect
as a PostgreSQL administrator and grant the application role access:

```sql
GRANT USAGE ON SCHEMA public TO qr_attendance_app;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO qr_attendance_app;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO qr_attendance_app;
```

### Frontend Setup

Open another terminal in the frontend directory:

```bash
cd frontend
npm install
npm run dev
```

Follow the terminal output to open the local development URL.

### Start everything with one command

From the project root, set the database credentials and run:

```bash
export DB_USER=your_postgres_user
export DB_PASSWORD=your_postgres_password
./run.sh
```

The launcher defaults `DB_USER` to `qr_attendance_app`; override it with
`DB_USER=postgres` if needed. If `DB_PASSWORD` is not exported, the launcher
prompts for it securely without displaying the password.

On Windows, run the same script from Git Bash. If Maven is missing and
`winget` is available, the script installs Apache Maven automatically. Press
`Ctrl+C` to stop both the backend and frontend processes. Database credentials
must be supplied through environment variables; they are never stored in the
script.

### MVP demo

1. Open `http://localhost:5173/table/1` (or scan a QR code whose value is the
   table's numeric ID or its `qr_token`).
2. Select menu items, open the cart, and place the order.
3. Use the confirmation order URL to watch status updates.
4. Set `STAFF_ACCESS_CODE` before starting `run.sh`; the launcher passes it to both
the backend and frontend. Then open `/staff-access` and enter it. Use the internal kitchen workspace
   to move the order from `PREPARING`
   to `READY` or `SERVED`.
5. Open the internal orders or admin workspace after staff sign-in. Customer
   routes do not expose these screens.

### HTTP API examples

```bash
curl http://localhost:8080/api/menu
curl http://localhost:8080/api/tables/1
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d "{\"tableToken\":\"1\",\"items\":[{\"menuItemId\":1,\"quantity\":2}]}"
curl -X PATCH http://localhost:8080/api/orders/1 \
  -H "Content-Type: application/json" \
  -d "{\"status\":\"READY\"}"
curl http://localhost:8080/api/orders/1
```

The HTTP adapter resolves the QR token server-side and does not trust a
client-supplied table ID. Errors return a stable `code` and user-safe
`message`; database details are logged only on the server.

**Note:** The backend server, database integration, and frontend communication must be implemented and configured before the complete application can run.

## Team Collaboration

The project is developed collaboratively by a team of three members.

- **Networking and Backend:** TCP server, client connections, protocol, concurrency, and message handling.
- **Customer Interface:** QR-based menu, cart, checkout, and order tracking.
- **Kitchen and Management:** Kitchen dashboard, waiter dashboard, administration, and database integration.

Responsibilities can overlap during integration and testing.

## Academic Purpose

TableBite is developed as an Operating Systems and Network Programming project to demonstrate client-server communication, TCP socket programming, concurrent client handling, synchronization, and real-time data exchange.

## Project Status

**Status:** Initial project structure and development setup.

Features will be marked complete as implementation and testing progress.

## License

This project is intended for educational purposes.
