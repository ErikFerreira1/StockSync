-- Legacy values were written with LocalDateTime by a JVM running in America/Recife.
-- PostgreSQL needs this assumption because TIMESTAMP rows do not contain an offset.
ALTER TABLE integration_credentials
    ALTER COLUMN expires_at TYPE TIMESTAMPTZ
    USING expires_at AT TIME ZONE 'America/Recife';

ALTER TABLE inventory
    ALTER COLUMN updated_at DROP DEFAULT,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ
    USING updated_at AT TIME ZONE 'America/Recife',
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE marketplace_listings
    ALTER COLUMN last_synced_at TYPE TIMESTAMPTZ
    USING last_synced_at AT TIME ZONE 'America/Recife';

ALTER TABLE orders
    ALTER COLUMN order_date TYPE TIMESTAMPTZ
    USING order_date AT TIME ZONE 'America/Recife';

ALTER TABLE stock_movements
    ALTER COLUMN occurred_at TYPE TIMESTAMPTZ
    USING occurred_at AT TIME ZONE 'America/Recife';

ALTER TABLE sync_events
    ALTER COLUMN event_timestamp TYPE TIMESTAMPTZ
    USING event_timestamp AT TIME ZONE 'America/Recife';
