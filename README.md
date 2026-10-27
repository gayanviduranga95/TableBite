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
```

Configure the database connection in `backend/src/main/resources/application.properties` and create the required MySQL database before running the application.

### Frontend Setup

Open another terminal in the frontend directory:

```bash
cd frontend
npm install
npm run dev
```

Follow the terminal output to open the local development URL.

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
