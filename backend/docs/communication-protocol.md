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

## 3. Raw TCP protocol (port 5000)

Newline-delimited JSON, one request per line (max 64 KB), 30 s idle timeout.

| Request | Response |
|---|---|
| `{"type":"PING"}` | `{"type":"PONG","message":"TableBite server is running"}` |
| `{"type":"GET_MENU"}` | array of available dishes |
| `{"type":"NEW_ORDER","tableId":1,"items":[{"menuItemId":1,"quantity":2}]}` | `{"type":"ORDER_CREATED","success":true,"orderId":…,"status":"PENDING"}` |
| `{"type":"ORDER_STATUS_UPDATE","orderId":1,"status":"CONFIRMED"}` | `{"type":"ORDER_STATUS_UPDATED",…}` |
| `{"type":"GET_ORDERS"}` / `{"type":"GET_ORDER","orderId":1}` / `GET_ORDER_STATUS` | order data |

Errors: `{"type":"ERROR","success":false,"code":"…","message":"…"}`; the connection
stays usable after an error. TCP orders and status changes are also pushed to
WebSocket subscribers.
