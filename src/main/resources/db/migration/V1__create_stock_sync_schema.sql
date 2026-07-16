CREATE TABLE sales_channels (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(50) NOT NULL,
    base_url VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_sales_channels_name UNIQUE (name)
);

CREATE TABLE integration_credentials (
    id BIGSERIAL PRIMARY KEY,
    sales_channel_id BIGINT NOT NULL UNIQUE,
    client_id VARCHAR(255) NOT NULL,
    client_secret_encrypted TEXT NOT NULL,
    access_token TEXT,
    refresh_token TEXT,
    expires_at TIMESTAMP,
    CONSTRAINT fk_integration_credentials_sales_channel
        FOREIGN KEY (sales_channel_id) REFERENCES sales_channels (id)
);

CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    base_price NUMERIC(19, 2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_products_sku UNIQUE (sku),
    CONSTRAINT ck_products_base_price_non_negative CHECK (base_price >= 0)
);

CREATE TABLE inventory (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL UNIQUE,
    available_quantity INTEGER NOT NULL DEFAULT 0,
    min_quantity INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_inventory_available_quantity_non_negative CHECK (available_quantity >= 0),
    CONSTRAINT ck_inventory_min_quantity_non_negative CHECK (min_quantity >= 0)
);

CREATE TABLE marketplace_listings (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    sales_channel_id BIGINT NOT NULL,
    listing_id VARCHAR(255) NOT NULL,
    listing_url VARCHAR(1000),
    status VARCHAR(50) NOT NULL,
    last_synced_at TIMESTAMP,
    CONSTRAINT fk_marketplace_listings_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_marketplace_listings_sales_channel FOREIGN KEY (sales_channel_id) REFERENCES sales_channels (id),
    CONSTRAINT uk_marketplace_listings_channel_listing UNIQUE (sales_channel_id, listing_id)
);

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    sales_channel_id BIGINT NOT NULL,
    external_order_id VARCHAR(255) NOT NULL,
    order_date TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    CONSTRAINT fk_orders_sales_channel FOREIGN KEY (sales_channel_id) REFERENCES sales_channels (id),
    CONSTRAINT uk_orders_channel_external_order UNIQUE (sales_channel_id, external_order_id)
);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(19, 2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_order_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_order_items_unit_price_non_negative CHECK (unit_price >= 0)
);

CREATE TABLE stock_movements (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    order_id BIGINT,
    occurred_at TIMESTAMP NOT NULL,
    quantity INTEGER NOT NULL,
    type VARCHAR(50) NOT NULL,
    origin_type VARCHAR(50) NOT NULL,
    origin_id BIGINT,
    note TEXT,
    CONSTRAINT fk_stock_movements_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_stock_movements_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_stock_movements_quantity_non_zero CHECK (quantity <> 0)
);

CREATE TABLE sync_events (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    sales_channel_id BIGINT NOT NULL,
    order_id BIGINT,
    event_timestamp TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    error_message TEXT,
    attempts INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_sync_events_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_sync_events_sales_channel FOREIGN KEY (sales_channel_id) REFERENCES sales_channels (id),
    CONSTRAINT fk_sync_events_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_sync_events_attempts_non_negative CHECK (attempts >= 0)
);

CREATE INDEX idx_marketplace_listings_product_id ON marketplace_listings (product_id);
CREATE INDEX idx_orders_sales_channel_id ON orders (sales_channel_id);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_product_id ON order_items (product_id);
CREATE INDEX idx_stock_movements_product_occurred_at ON stock_movements (product_id, occurred_at);
CREATE INDEX idx_stock_movements_order_id ON stock_movements (order_id);
CREATE INDEX idx_sync_events_product_id ON sync_events (product_id);
CREATE INDEX idx_sync_events_sales_channel_id ON sync_events (sales_channel_id);
CREATE INDEX idx_sync_events_order_id ON sync_events (order_id);
