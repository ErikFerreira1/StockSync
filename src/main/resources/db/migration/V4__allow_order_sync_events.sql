ALTER TABLE sync_events ALTER COLUMN product_id DROP NOT NULL;

ALTER TABLE sync_events ADD COLUMN external_order_id VARCHAR(255);

CREATE INDEX idx_sync_events_channel_external_order
    ON sync_events (sales_channel_id, external_order_id);
