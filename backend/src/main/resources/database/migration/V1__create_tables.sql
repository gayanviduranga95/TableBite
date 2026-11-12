-- Starter schema for a NEW PostgreSQL database. Do not apply blindly to existing schemas.
CREATE TABLE IF NOT EXISTS restaurant_tables (
    table_id BIGSERIAL PRIMARY KEY,
    table_number VARCHAR(20) NOT NULL UNIQUE,
    qr_token VARCHAR(128) NOT NULL UNIQUE
);
CREATE TABLE IF NOT EXISTS categories (
    category_id BIGSERIAL PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL UNIQUE
);
CREATE TABLE IF NOT EXISTS menu_items (
    menu_item_id BIGSERIAL PRIMARY KEY,
    item_name VARCHAR(150) NOT NULL,
    description TEXT,
    price NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    available BOOLEAN NOT NULL DEFAULT TRUE,
    category_id BIGINT REFERENCES categories(category_id)
);
CREATE TABLE IF NOT EXISTS orders (
    order_id BIGSERIAL PRIMARY KEY,
    table_id BIGINT NOT NULL REFERENCES restaurant_tables(table_id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
       CHECK (status IN ('PENDING','ACCEPTED','PREPARING','READY','SERVED','CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS order_items (
    order_item_id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(order_id),
    menu_item_id BIGINT NOT NULL REFERENCES menu_items(menu_item_id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0)
);
CREATE INDEX IF NOT EXISTS idx_orders_table ON orders(table_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items(order_id);
