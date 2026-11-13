# TableBite

TableBite is a restaurant ordering system built with Java and Vue. Guests scan the QR code on their table, browse the menu and order from their phone. The kitchen sees the order straight away, and the guest watches its status change in real time. Admins manage the menu, the tables and their QR codes, orders, payments and staff from one dashboard.

It is built around network-programming concepts: an HTTP REST API, a WebSocket notification server and a raw TCP protocol server all run at the same time. They use thread pools, PostgreSQL through JDBC and transactional order handling.

| | |
|---|---|
| **Backend** | Java 21, Maven, JDK `HttpServer`, Java-WebSocket, JDBC + PostgreSQL, Jackson |
| **Frontend** | Vue 3, Vite, Vue Router, Tailwind CSS 4, lucide icons, `qrcode`, `jsQR` |
| **Ports** | HTTP 8080 · WebSocket 8081 · TCP 5000 · Vite 5173 |

---

## 1. Quick start

### Requirements
- **JDK 21+**: Maven must run with it. If `mvn -v` shows Java 8, set `JAVA_HOME` (see below).
- **Maven 3.9+**, **Node.js 20+ / npm**
- **PostgreSQL 13+** running on `localhost:5432`

### 1.1 Database
If you already have a `tablebite_db` database, skip this step. TableBite upgrades it automatically and never deletes data.

For a new installation:
```bash
psql -U postgres -f backend/scripts/setup-database.sql   # creates role tablebite_app + database
```
(Or simply `createdb tablebite_db` and use your own PostgreSQL user.)

All tables, upgrades and demo data are created by the backend when it starts (`DB_AUTO_MIGRATE=true`). See [backend/docs/database-design.md](backend/docs/database-design.md).

### 1.2 Start everything (Git Bash / Linux / macOS)
```bash
export DB_USER=postgres
export DB_PASSWORD=your_password
./run.sh
```
`run.sh` picks a JDK 21+, builds the backend, installs the frontend packages, and starts both.

### 1.3 Or start each part yourself

**Backend (PowerShell)**
```powershell
cd backend
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"   # any JDK 21+
mvn -q -DskipTests compile dependency:build-classpath "-Dmdep.outputFile=target/runtime-classpath.txt" "-Dmdep.includeScope=runtime"
$env:DB_USER = "postgres"
$env:DB_PASSWORD = "your_password"
& "$env:JAVA_HOME\bin\java" -cp "target/classes;$(Get-Content target/runtime-classpath.txt -Raw)" com.tablebite.Main
```
On Linux/macOS, use `:` instead of `;` in the classpath.

**Frontend**
```bash
cd frontend
npm install
npm run dev            # http://localhost:5173  (also printed: a Network URL for phones)
```

All backend settings are environment variables; see [backend/.env.example](backend/.env.example). No credentials are stored in the code.

### 1.4 Open the app
| What | URL |
|---|---|
| Customer site | http://localhost:5173/ |
| Demo table (simulates a QR scan) | http://localhost:5173/table/tablebite-demo-t01-8c9d4d1e |
| Kitchen display (enter the kitchen code) | http://localhost:5173/kitchen |
| Admin / waiter sign in | http://localhost:5173/staff/login |
| Floor (waiter) view | http://localhost:5173/floor |
| Admin dashboard | http://localhost:5173/admin |

### 1.5 Access
- **Customers** don't sign in. They scan the table QR code (or browse the menu) and order as guests. Their orders are remembered on their device under **My orders**.
- **Kitchen:** open `/kitchen` and type the **kitchen code**. The default is `kitchen123`; set your own with `KITCHEN_ACCESS_CODE`. The kitchen screen stays unlocked on that device until someone presses the lock button (top right). The code only gives kitchen rights (accept, prepare, mark ready, reject, mark dishes sold out), and 8 wrong tries lock that device out for 10 minutes.
- **Admin and waiters** sign in at `/staff/login`. Development accounts created on first start:

| Role | Email | Password |
|---|---|---|
| Admin | `admin@tablebite.local` | `Admin@12345` |
| Waiter / floor | `waiter@tablebite.local` | `Waiter@12345` |

For production, set `ADMIN_EMAIL` / `ADMIN_PASSWORD` before the first start, `SEED_DEMO_ACCOUNTS=false`, and your own `KITCHEN_ACCESS_CODE`.

---

## 2. QR codes

### Generate, download and print
1. Sign in as admin and open **Tables & QR** (`/admin/tables`).
2. Each table card shows its live QR code. Use **PNG** to download a branded, print-ready card, **Print** for one table, or **Print all** for an A4 sheet of every table.
3. Use **Add table** to create a table with a new secure token. **Generate new QR code** (↻) invalidates the old printed code.

A QR code contains a URL like `http://<host>/table/tb-3f9c…`. The token is 128 random bits, so table codes cannot be guessed.

> **Important:** QR codes point to the **public ordering URL**. `localhost` only works on the computer itself, so before printing, go to **Settings → Public ordering URL** and enter your computer's LAN address, for example `http://192.168.1.20:5173`. Vite prints it as the *Network* URL.

### Test QR scanning
- **On the computer:** open `/scan`. The camera works on `localhost`. Hold a printed or on-screen QR code up to the webcam, or use **Scan from a photo** with a downloaded PNG.
- **On a phone (same Wi-Fi):**
  1. Browsers only allow live camera access over HTTPS, so run `npm run dev:https` and open `https://<your-ip>:5173`. Accept the self-signed certificate warning once.
  2. Set the Public ordering URL to that `https://<your-ip>:5173` address and print or download the QR codes.
  3. Point the phone's normal camera app at the QR code. It opens the menu with the table already selected. Inside the app, `/scan` also works.
  4. Without HTTPS, **Scan from a photo** and the manual table number fallback still work.
- If Windows Firewall asks, allow Node.js on private networks so phones can reach port 5173.
- Invalid or old codes show a friendly "We couldn't recognise this table" page with options to scan again or enter the table number.

---

## 3. Features

### Customer (mobile-first)
- Home page with a hero section, search, **Scan QR** button, category icons, popular, recommended and new-arrival sections, an order-tracking shortcut and a footer.
- **QR table ordering**: scanning sets the table automatically, which shows in the header and menu. Manual table-number fallback (admin can turn it off). Invalid codes are handled.
- Menu with search, category filter, price filter, vegetarian filter, sorting (popular, price, name), sold-out indicators and a dish details page.
- **38 dishes with real food photos** stored locally (`frontend/public/images/menu`), plus a fallback image so an image is never broken.
- Cart that is saved across refreshes: quantity steppers, per-item notes, special instructions, clear cart, and sold-out items blocked. Shows subtotal, tax, service charge (dine-in only) and total.
- Dine-in or takeaway. Payment choice: cash or pay at counter (online appears only when a gateway is configured).
- Order confirmation with an order number (`TB-YYMMDD-NNNN`), **live status tracking** through WebSockets with a polling fallback, a progress tracker, a timeline, cancellation while still pending, and **reorder**.
- No sign-up needed: order history is kept for guests on their device.
- Toasts for every update, a browser notification when the tab is in the background, and a floating "your order is being prepared" banner.

### Admin console (`/admin`)
- Dashboard: total and today's revenue and orders, average order, pending / preparing / completed counts, tables in use, daily revenue chart, orders by hour, popular dishes and recent orders. **Updates live.**
- Orders: search (number, table, customer, phone), filters (status, type, date, payment), accept / reject / update status, order details with customer, table, items, notes and timeline.
- Menu: add, edit and delete dishes, **upload photos** (JPEG/PNG/WebP up to 5 MB), price, category, prep time, available / popular / recommended / vegetarian / spicy flags. Manage categories (icon, order, visibility).
- Tables & QR: add, edit and delete tables, status, seats, location, QR generation, download, print, regenerate, and orders per table.
- Payments: payment history with filters and collected / outstanding totals.
- Settings: restaurant details, currency, tax %, service charge %, takeaway on/off, manual table selection on/off, public QR URL, and **staff accounts and roles**.

### Kitchen display (`/kitchen`, unlocked with the kitchen code)
- Full-screen dark layout with New / Accepted / Cooking / Ready columns, large table numbers, items, quantities, notes and special instructions, and order time.
- Accept, Start preparing, Mark ready and Reject in one tap. Elapsed timers highlight **urgent orders** (amber after 15 min, red after 25). Sound alert for new orders, auto-refresh (WebSocket plus 30 s polling), and full-screen mode.

### Floor / waiter view (`/floor`)
- Ready-to-serve queue, **Mark served** / **Picked up**, an awaiting-payment list with one-tap **Paid**, completing served orders, and a live table map.

### Roles

| | Customer | Kitchen | Waiter | Admin |
|---|:-:|:-:|:-:|:-:|
| Order, track, cancel own pending order | ✓ | | | |
| Kitchen display, accept / prepare / ready, mark dish sold out | | ✓ | | ✓ |
| Serve / complete orders, record payments, floor view | | | ✓ | ✓ |
| Reject / cancel orders, order list | | ✓ | ✓ | ✓ |
| Dashboard, menu, tables, settings, users | | | | ✓ |

---

## 4. Payments
- Every order creates a **pending payment**. Staff confirm cash, card-terminal or counter payments from the floor view or the order details. Payments record the method, amount, reference, who received it and when, and a payment cannot be confirmed twice.
- **Online payments are not simulated.** The architecture is ready for a gateway:
  1. Implement `com.tablebite.payment.PaymentGateway` for your provider (for example PayHere or Stripe Checkout). `createCheckout` creates a hosted checkout session for the order total, and `verifyWebhook` verifies the provider's signature.
  2. Return it from `PaymentGateways.configured()` when the provider secrets are present in the environment.
  3. Point the provider webhook to `POST /api/payments/webhook`. Payments become `PAID` only from a verified webhook.

  Once configured, `/api/settings` reports `online_payment_enabled: true` and the card / online option becomes selectable at checkout.

---

## 5. Notifications
WebSocket events (`ORDER_CREATED`, `ORDER_STATUS_CHANGED`, `PAYMENT_UPDATED`) carry a readable message such as "Order TB-261113-0042 is ready and on its way to your table":
- **Staff screens:** chime and toast for new orders, and screens refresh instantly.
- **Customers:** live tracking page with toasts, a vibration on supported phones, and a system notification when the tab is in the background (permission is requested after the first order).

Protocol details are in [backend/docs/communication-protocol.md](backend/docs/communication-protocol.md).

---

## 6. Testing
```bash
cd backend && mvn test   # with JAVA_HOME set to a JDK 21+
```
Unit tests cover the TCP client handler, order status rules, password hashing and tokens, the router, upload validation and input validation. The full HTTP + WebSocket flow (auth, QR tokens, pricing, idempotency, the role-based workflow, payments, uploads, admin operations, notifications and rate limiting) was verified end-to-end against a temporary PostgreSQL database.

---

## 7. Project structure
```text
TableBite/
├── backend/
│   ├── docs/                     architecture, database design, protocols
│   ├── scripts/setup-database.sql
│   ├── .env.example              all environment variables
│   └── src/main/java/com/tablebite/
│       ├── Main.java             starts HTTP + WebSocket + TCP servers
│       ├── database/             JDBC repositories, SchemaMigrator, Jdbc helpers
│       ├── model/                Order, MenuItem, OrderStatus, UserRole, …
│       ├── service/              Authentication, Order, Menu, Table, Settings, Notification
│       ├── payment/              PaymentGateway integration point
│       ├── server/               HttpApiServer, LiveWebSocketServer, TcpServer, ClientHandler
│       │   └── api/              Router, ApiContext, CustomerRoutes, StaffRoutes, UploadStore
│       └── util/                 SecurityUtil (PBKDF2, tokens), JsonUtil, LoggerUtil
│   └── src/main/resources/database/migration/  V1–V4 (idempotent)
├── frontend/
│   ├── public/images/            menu photos, hero images, fallback image
│   └── src/
│       ├── views/customer|admin|kitchen|waiter|staff
│       ├── components/           FoodCard, QrScanner, OrderProgress, BarChart, …
│       ├── layouts/              CustomerLayout, StaffLayout
│       ├── services/             api.js, orderService.js, socketService.js
│       ├── stores/               auth, settings, cart/table (persisted)
│       ├── composables/          toasts, confirm dialog, order actions, staff events
│       └── utils/                formatters, status metadata, images, QR helpers
└── run.sh
```

---

## 8. Known limitations
- **Online card payment** needs a real gateway integration (see §4). Only in-person payments are active.
- **Phone camera scanning over the LAN** needs HTTPS (`npm run dev:https`, self-signed certificate). Photo scanning and manual entry work without it.
- Notifications are in-app, WebSocket and browser notifications only. There is no SMS or e-mail and no push notifications while the browser is fully closed.
- A single restaurant (one menu and one set of tables) per database.
- The development server (`npm run dev`) is meant for local or LAN use. For a public deployment, build the frontend (`npm run build`) and serve `dist/` behind a reverse proxy that forwards `/api`, `/uploads` and `/ws` to the backend, with HTTPS.
- Uploaded images are stored on the local disk (`UPLOAD_DIR`). Back that folder up together with the database.
- The kitchen code is shared by everyone in the kitchen, so change it when staff leave. (`STAFF_ACCESS_CODE` from the original project still works as the kitchen code if `KITCHEN_ACCESS_CODE` isn't set.)
- Customer accounts still exist in the backend API but are hidden from the app; everyone orders as a guest.

Food photography: [Unsplash](https://unsplash.com) (Unsplash License, free to use).

## License
For educational and academic use.
