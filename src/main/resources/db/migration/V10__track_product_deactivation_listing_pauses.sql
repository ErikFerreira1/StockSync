ALTER TABLE marketplace_listings
    ADD COLUMN paused_by_product_deactivation BOOLEAN NOT NULL DEFAULT FALSE;
