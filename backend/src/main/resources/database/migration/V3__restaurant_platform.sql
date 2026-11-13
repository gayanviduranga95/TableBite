-- Scripts contain UTF-8 text (e.g. "à", "–"); make psql read them correctly on Windows.
SET client_encoding = 'UTF8';

-- =====================================================================
-- V3: TableBite restaurant platform upgrade
--
-- This migration is written to be SAFE on an existing database:
--   * only ADD COLUMN IF NOT EXISTS / CREATE ... IF NOT EXISTS
--   * existing rows are back-filled, never deleted
--   * legacy ACCEPTED status is mapped to the new CONFIRMED status
-- It can be executed more than once without side effects.
-- =====================================================================

-- ---------- Categories ----------
ALTER TABLE categories ADD COLUMN IF NOT EXISTS icon VARCHAR(40);
ALTER TABLE categories ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 100;
ALTER TABLE categories ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

-- ---------- Menu items ----------
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS image_url VARCHAR(500);
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS is_popular BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS is_recommended BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS is_vegetarian BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS is_spicy BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS prep_minutes INTEGER NOT NULL DEFAULT 15;
-- Existing dishes are dated in the past so they are not shown as "new arrivals".
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;
UPDATE menu_items SET created_at = NOW() - INTERVAL '90 days' WHERE created_at IS NULL;
ALTER TABLE menu_items ALTER COLUMN created_at SET DEFAULT NOW();
ALTER TABLE menu_items ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
-- Soft delete: order_items keep referencing deleted dishes for history.
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_menu_items_category ON menu_items(category_id);
CREATE INDEX IF NOT EXISTS idx_menu_items_live ON menu_items(available) WHERE deleted_at IS NULL;

-- ---------- Restaurant tables ----------
ALTER TABLE restaurant_tables ADD COLUMN IF NOT EXISTS seats INTEGER NOT NULL DEFAULT 4;
ALTER TABLE restaurant_tables ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE';
ALTER TABLE restaurant_tables ADD COLUMN IF NOT EXISTS location VARCHAR(60);
ALTER TABLE restaurant_tables ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE restaurant_tables ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- ---------- Users & sessions ----------
CREATE TABLE IF NOT EXISTS users (
    user_id       BIGSERIAL PRIMARY KEY,
    full_name     VARCHAR(120) NOT NULL,
    email         VARCHAR(160) NOT NULL,
    phone         VARCHAR(30),
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER',
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email ON users (LOWER(email));

CREATE TABLE IF NOT EXISTS user_sessions (
    session_id  BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token_hash  CHAR(64)    NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ NOT NULL,
    user_agent  VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS idx_user_sessions_user ON user_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_user_sessions_expiry ON user_sessions(expires_at);

-- ---------- Orders ----------
-- Takeaway orders have no table.
ALTER TABLE orders ALTER COLUMN table_id DROP NOT NULL;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS order_number VARCHAR(30);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES users(user_id) ON DELETE SET NULL;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS order_type VARCHAR(12) NOT NULL DEFAULT 'DINE_IN';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_name VARCHAR(120);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_phone VARCHAR(30);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS special_instructions VARCHAR(500);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS subtotal NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS service_charge NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS total_amount NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_method VARCHAR(20) NOT NULL DEFAULT 'PAY_AT_COUNTER';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tracking_token VARCHAR(64);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(80);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancel_reason VARCHAR(255);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- Replace the legacy status CHECK (it did not know CONFIRMED / COMPLETED).
DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'orders'::regclass AND contype = 'c'
          AND conname NOT LIKE 'chk_orders_%'
          AND pg_get_constraintdef(oid) ILIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE orders DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;

UPDATE orders SET status = 'CONFIRMED' WHERE status = 'ACCEPTED';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_orders_status') THEN
        ALTER TABLE orders ADD CONSTRAINT chk_orders_status CHECK (status IN
            ('PENDING','CONFIRMED','PREPARING','READY','SERVED','COMPLETED','CANCELLED'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_orders_type') THEN
        ALTER TABLE orders ADD CONSTRAINT chk_orders_type CHECK (order_type IN ('DINE_IN','TAKEAWAY'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_orders_dine_in_table') THEN
        ALTER TABLE orders ADD CONSTRAINT chk_orders_dine_in_table
            CHECK (order_type <> 'DINE_IN' OR table_id IS NOT NULL);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_orders_payment_method') THEN
        ALTER TABLE orders ADD CONSTRAINT chk_orders_payment_method
            CHECK (payment_method IN ('CASH','PAY_AT_COUNTER','CARD','ONLINE'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_orders_payment_status') THEN
        ALTER TABLE orders ADD CONSTRAINT chk_orders_payment_status
            CHECK (payment_status IN ('UNPAID','PENDING','PAID','REFUNDED','FAILED'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_orders_amounts') THEN
        ALTER TABLE orders ADD CONSTRAINT chk_orders_amounts
            CHECK (subtotal >= 0 AND tax_amount >= 0 AND service_charge >= 0 AND total_amount >= 0);
    END IF;
END $$;

-- ---------- Order items ----------
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS item_name VARCHAR(150);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS notes VARCHAR(255);

UPDATE order_items oi SET item_name = mi.item_name
FROM menu_items mi
WHERE oi.menu_item_id = mi.menu_item_id AND oi.item_name IS NULL;

-- ---------- Back-fill legacy orders ----------
UPDATE orders o
SET subtotal = t.sum_total, total_amount = t.sum_total
FROM (SELECT order_id, COALESCE(SUM(quantity * unit_price), 0) AS sum_total
      FROM order_items GROUP BY order_id) t
WHERE o.order_id = t.order_id AND o.total_amount = 0;

UPDATE orders
SET order_number = 'TB-' || to_char(created_at, 'YYMMDD') || '-' || lpad(order_id::text, 4, '0')
WHERE order_number IS NULL;

UPDATE orders
SET tracking_token = replace(gen_random_uuid()::text, '-', '')
WHERE tracking_token IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_orders_order_number ON orders(order_number);
CREATE UNIQUE INDEX IF NOT EXISTS ux_orders_idempotency ON orders(idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_orders_user ON orders(user_id);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_order_items_menu_item ON order_items(menu_item_id);

-- ---------- Order status history ----------
CREATE TABLE IF NOT EXISTS order_status_history (
    history_id  BIGSERIAL PRIMARY KEY,
    order_id    BIGINT      NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status   VARCHAR(20) NOT NULL,
    changed_by  BIGINT REFERENCES users(user_id) ON DELETE SET NULL,
    note        VARCHAR(255),
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_order_status_history_order ON order_status_history(order_id);

-- ---------- Payments ----------
CREATE TABLE IF NOT EXISTS payments (
    payment_id  BIGSERIAL PRIMARY KEY,
    order_id    BIGINT        NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    method      VARCHAR(20)   NOT NULL,
    status      VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    amount      NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    provider    VARCHAR(40),
    reference   VARCHAR(100),
    received_by BIGINT REFERENCES users(user_id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    paid_at     TIMESTAMPTZ,
    CONSTRAINT chk_payments_method CHECK (method IN ('CASH','PAY_AT_COUNTER','CARD','ONLINE')),
    CONSTRAINT chk_payments_status CHECK (status IN ('PENDING','PAID','FAILED','REFUNDED','CANCELLED'))
);
CREATE INDEX IF NOT EXISTS idx_payments_order ON payments(order_id);
CREATE INDEX IF NOT EXISTS idx_payments_created ON payments(created_at DESC);

-- ---------- Restaurant settings ----------
CREATE TABLE IF NOT EXISTS restaurant_settings (
    setting_key   VARCHAR(60) PRIMARY KEY,
    setting_value VARCHAR(500) NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO restaurant_settings (setting_key, setting_value) VALUES
    ('restaurant_name', 'TableBite'),
    ('tagline', 'Fresh food, served to your table'),
    ('address', '42 Galle Road, Colombo 03'),
    ('phone', '+94 11 234 5678'),
    ('opening_hours', 'Daily 10:00 – 23:00'),
    ('currency', 'Rs'),
    ('tax_rate', '0'),
    ('service_charge_rate', '10'),
    ('allow_manual_table_selection', 'true'),
    ('allow_takeaway', 'true'),
    ('public_base_url', '')
ON CONFLICT (setting_key) DO NOTHING;

-- ---------- Table status constraint ----------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_tables_status') THEN
        ALTER TABLE restaurant_tables ADD CONSTRAINT chk_tables_status
            CHECK (status IN ('AVAILABLE','OCCUPIED','RESERVED','OUT_OF_SERVICE'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_users_role') THEN
        ALTER TABLE users ADD CONSTRAINT chk_users_role
            CHECK (role IN ('CUSTOMER','ADMIN','KITCHEN','WAITER'));
    END IF;
END $$;
