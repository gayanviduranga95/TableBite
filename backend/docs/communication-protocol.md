# TableBite – Communication Protocols

TableBite runs three servers at the same time.

## 1. REST API (HTTP, port 8080, prefix `/api`)

JSON in / JSON out. Authenticated calls send `Authorization: Bearer <token>`.
Errors: `{ "success": false, "code": "VALIDATION_ERROR", "message": "…" }` with
400 / 401 / 403 / 404 / 405 / 409 / 413 / 429 / 500.

### Public & customer

| Method | Path | Notes |
|---|---|---|
| GET | `/health` | liveness |
| GET | `/settings` | restaurant info, tax/service rates, feature flags |
| GET | `/menu`, `/menu/{id}`, `/categories` | customer menu (includes sold-out flag) |
| GET | `/tables/{qrToken}` | resolve a scanned QR code |
| POST | `/tables/lookup` `{tableNumber}` | manual table fallback (if enabled) |
| POST | `/auth/register` `{fullName,email,phone?,password}` | returns `{token, expiresAt, user}` |
| POST | `/auth/login` `{email,password}` | rate limited |
| POST | `/auth/logout` | revokes the session |
| GET/PUT | `/auth/me` | profile |
| PUT | `/auth/password` `{currentPassword,newPassword}` | |
| GET | `/me/orders` | signed-in customer's history (with items) |
| POST | `/orders` | place order (see below) |
| GET | `/orders/{id}?token=` | owner, staff, or holder of the tracking token |
| POST | `/orders/{id}/cancel` `{token?,reason?}` | only while `PENDING` |

`POST /orders` body:

```json
{
  "orderType": "DINE_IN",
  "tableToken": "tablebite-demo-t01-8c9d4d1e",
  "items": [{ "menuItemId": 7, "quantity": 2, "notes": "no onions" }],
  "specialInstructions": "Birthday!",
  "customerName": "Nimal", "customerPhone": "0771234567",
  "paymentMethod": "PAY_AT_COUNTER",
  "idempotencyKey": "7b0d3c1e-…"
}
```

Response `201` (or `200` with `"duplicate": true` for a repeated idempotency key):
`{ orderId, orderNumber, status, trackingToken, duplicate, order }`.
Prices, tax and service charge are always computed by the server.

### Staff

| Method | Path | Roles |
|---|---|---|
| GET | `/orders?status=&q=&type=&range=today&payment=&active=&items=` | any staff |
| PATCH | `/orders/{id}` `{status, reason?}` | kitchen: CONFIRMED/PREPARING/READY; floor: SERVED/COMPLETED; any staff: CANCELLED |
| POST | `/orders/{id}/payments` `{method: CASH|CARD|PAY_AT_COUNTER, reference?}` | admin, waiter |
| GET | `/kitchen/orders` | admin, kitchen |
| PATCH | `/admin/menu/{id}/availability` `{available}` | any staff (mark sold out) |
| GET | `/admin/tables`, `/admin/tables/{id}/orders`, `/admin/payments` | staff |
| GET | `/admin/stats?days=14` | admin |
| GET/POST/PUT/DELETE | `/admin/menu[/{id}]`, `/admin/categories[/{id}]` | admin |
| POST | `/admin/uploads` (raw JPEG/PNG/WebP body, ≤ 5 MB) → `{url}` | admin |
| POST/PUT/DELETE | `/admin/tables[/{id}]`, POST `/admin/tables/{id}/regenerate` | admin |
| GET/PUT | `/admin/settings` | admin |
| GET/POST | `/admin/users`, PATCH `/admin/users/{id}` `{role, active}` | admin |
| POST | `/payments/webhook` | only when an online gateway is configured |

### Order status workflow

```
PENDING ──► CONFIRMED ──► PREPARING ──► READY ──► SERVED ──► COMPLETED
   │            │                         └──────────────────► COMPLETED (takeaway pick-up)
   └────────────┴──► CANCELLED
```

Invalid transitions return 400. The legacy value `ACCEPTED` is accepted as `CONFIRMED`.

## 2. WebSocket notifications (port 8081, proxied at `/ws`)

Client → server:

```json
{ "type": "SUBSCRIBE", "channel": "staff", "token": "<staff session token>" }
{ "type": "SUBSCRIBE", "channel": "order", "orderId": 42, "trackingToken": "…" }
{ "type": "SUBSCRIBE", "channel": "user",  "token": "<customer session token>" }
{ "type": "PING" }
```

Server → client: `CONNECTED`, `SUBSCRIBED`, `PONG`, `ERROR`, and order events:

```json
{ "type": "ORDER_STATUS_CHANGED", "orderId": 42, "orderNumber": "TB-261113-0042",
  "status": "PREPARING", "paymentStatus": "UNPAID", "orderType": "DINE_IN",
  "tableNumber": "T01", "message": "Order TB-261113-0042 is being prepared",
  "order": { … full order without the tracking token … } }
```

Event types: `ORDER_CREATED`, `ORDER_STATUS_CHANGED`, `PAYMENT_UPDATED`.
The legacy `?accessCode=` / `"channel":"kitchen"` subscription still works when
`STAFF_ACCESS_CODE` is configured.

## 3. Raw TCP protocol (port 5000, bound to 127.0.0.1 by default)

Newline-delimited JSON (UTF-8), one request per line, one response line per request.
Limits: 64 KB per message (checked while reading), 30 s without a complete message
closes the connection (`IDLE_TIMEOUT`). Configure with `TCP_HOST`, `TCP_MAX_CLIENTS`,
`TCP_MAX_QUEUE`, `TCP_IDLE_TIMEOUT_SECONDS`.

A connection starts anonymous. Send `AUTH` once to act as a user; roles or user ids
sent in other messages are ignored.

| Request | Access | Response |
|---|---|---|
| `{"type":"PING"}` | anyone | `{"type":"PONG","message":"TableBite server is running"}` |
| `{"type":"GET_MENU"}` | anyone | array of available dishes |
| `{"type":"AUTH","token":"<session token>"}` or `{"type":"AUTH","accessCode":"<kitchen code>"}` | anyone | `{"type":"AUTH_OK","success":true,"userId":…,"role":"KITCHEN","name":…}` |
| `{"type":"NEW_ORDER","tableToken":"<table QR token>","items":[{"menuItemId":1,"quantity":2}]}` | anyone | `{"type":"ORDER_CREATED","success":true,"orderId":…,"orderNumber":…,"status":"PENDING","trackingToken":…}` |
| `{"type":"NEW_ORDER","tableId":1,"items":[…]}` | staff | `{"type":"ORDER_CREATED","success":true,"orderId":…,"status":"PENDING"}` |
| `{"type":"ORDER_STATUS_UPDATE","orderId":1,"status":"CONFIRMED","reason":"…"}` | staff (same role rules as `PATCH /orders/{id}`) | `{"type":"ORDER_STATUS_UPDATED","orderId":1,"status":"CONFIRMED"}` |
| `{"type":"GET_ORDERS"}` | staff | array of orders |
| `{"type":"GET_ORDER","orderId":1,"trackingToken":"…"}` / `GET_ORDER_STATUS` | staff, the signed-in owner, or the tracking-token holder | order / `{"type":"ORDER_STATUS",…}` |

Errors always look like `{"type":"ERROR","success":false,"code":"…","message":"…"}`.
Codes: `INVALID_JSON`, `VALIDATION_ERROR`, `UNKNOWN_MESSAGE_TYPE`, `MESSAGE_TOO_LARGE`,
`AUTH_REQUIRED`, `AUTH_FAILED`, `FORBIDDEN`, `TOO_MANY_REQUESTS`, `ORDER_NOT_FOUND`,
`TABLE_NOT_FOUND`, `INTERNAL_ERROR`, `*_FAILED` (database errors). The connection stays
usable after an error, except: `IDLE_TIMEOUT`, `MESSAGE_TOO_LARGE` for a stream with no
newline in 1 MB, and 5 failed `AUTH` attempts all close it.

Server-initiated lines: `SERVER_BUSY` error (all workers and queue slots full, then the
connection closes) and `{"type":"SERVER_SHUTDOWN"}` (sent before a graceful close).
TCP orders and status changes are also pushed to WebSocket subscribers.
