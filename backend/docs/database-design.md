# TableBite – Database Design (PostgreSQL)

Migrations live in `backend/src/main/resources/database/migration` and are applied
automatically on start-up by `SchemaMigrator` (recorded in
`tablebite_schema_migrations`). Every script is idempotent and never deletes data, so
it is safe on an existing database.

| Script | Purpose |
|---|---|
| `V1__create_tables.sql` | original tables: tables, categories, menu items, orders, order items |
| `V2__seed_initial_data.sql` | original demo data (T01, T02, Chicken Fried Rice, Lime Juice) |
| `V3__restaurant_platform.sql` | users, sessions, status history, payments, settings, new columns, constraints, indexes, back-fill |
| `V4__seed_full_menu.sql` | 9 categories, 38 dishes with photos, tables T03–T08, images for existing dishes |

## Entity relationships

```
users 1───* user_sessions
users 1───* orders (user_id, nullable: guests)          users 1───* payments (received_by)
restaurant_tables 1───* orders (table_id, nullable: takeaway)
orders 1───* order_items *───1 menu_items *───1 categories
orders 1───* order_status_history
orders 1───* payments
restaurant_settings (key/value)
```

## Tables

**restaurant_tables** – `table_id` PK, `table_number` UNIQUE, `qr_token` UNIQUE
(secure random), `seats`, `status` (AVAILABLE / OCCUPIED / RESERVED / OUT_OF_SERVICE),
`location`, `created_at`, `deleted_at` (archived tables keep order history).

**categories** – `category_id` PK, `category_name` UNIQUE, `icon`, `sort_order`, `active`.

**menu_items** – `menu_item_id` PK, `item_name`, `description`, `price NUMERIC(12,2) ≥ 0`,
`category_id` FK, `image_url`, `available`, `is_popular`, `is_recommended`,
`is_vegetarian`, `is_spicy`, `prep_minutes`, `created_at`, `updated_at`,
`deleted_at` (soft delete).

**users** – `user_id` PK, `full_name`, `email` (unique, case-insensitive index),
`phone`, `password_hash` (PBKDF2), `role` (CUSTOMER / ADMIN / KITCHEN / WAITER),
`active`, timestamps.

**user_sessions** – `session_id` PK, `user_id` FK (cascade), `token_hash` UNIQUE
(SHA-256 of the bearer token), `expires_at`, `user_agent`.

**orders** – `order_id` PK, `order_number` UNIQUE (`TB-YYMMDD-NNNN`), `table_id` FK
(nullable), `user_id` FK (nullable), `order_type` (DINE_IN / TAKEAWAY), `status`,
`customer_name`, `customer_phone`, `special_instructions`, `subtotal`, `tax_amount`,
`service_charge`, `total_amount`, `payment_method`, `payment_status`,
`tracking_token`, `idempotency_key` (unique when present), `cancel_reason`,
`created_at`, `updated_at`.
Checks: valid status / type / payment values, non-negative amounts, and
`order_type <> 'DINE_IN' OR table_id IS NOT NULL`.

**order_items** – `order_item_id` PK, `order_id` FK, `menu_item_id` FK, `quantity > 0`,
`unit_price` (price snapshot), `item_name` (name snapshot), `notes`.

**order_status_history** – every transition with `from_status`, `to_status`,
`changed_by` (user), `note`, `changed_at`.

**payments** – `payment_id` PK, `order_id` FK, `method`, `status`
(PENDING / PAID / FAILED / REFUNDED / CANCELLED), `amount`, `provider`, `reference`,
`received_by`, `created_at`, `paid_at`.

**restaurant_settings** – `setting_key` PK, `setting_value`: restaurant name, tagline,
address, phone, opening hours, currency, `tax_rate`, `service_charge_rate`,
`allow_manual_table_selection`, `allow_takeaway`, `public_base_url`.

## Indexes

`orders(table_id)`, `orders(status)`, `orders(user_id)`, `orders(created_at DESC)`,
`order_items(order_id)`, `order_items(menu_item_id)`, `menu_items(category_id)`,
partial `menu_items(available) WHERE deleted_at IS NULL`, `payments(order_id)`,
`payments(created_at DESC)`, `order_status_history(order_id)`,
`user_sessions(user_id)`, `user_sessions(expires_at)`.

## Transactions

* **Create order** – one transaction: lock & price dishes (`FOR SHARE`), insert the
  order, generate the order number, insert lines with price snapshots, create the
  pending payment and first history row, mark the table occupied.
* **Change status** – `SELECT … FOR UPDATE` on the order, validate the transition and
  caller rules, update, write history, cancel pending payments when cancelled, free
  the table when its last active order finishes.
* **Confirm payment** – lock the order, reject cancelled/already-paid orders, mark the
  payment and order paid.
