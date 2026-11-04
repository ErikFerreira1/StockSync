CREATE TABLE marketplace_publications (
    request_id UUID PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    product_id BIGINT NOT NULL REFERENCES products(id),
    sales_channel_id BIGINT NOT NULL REFERENCES sales_channels(id),
    state VARCHAR(30) NOT NULL,
    external_id VARCHAR(255),
    external_url VARCHAR(1000),
    external_status VARCHAR(50),
    listing_id BIGINT REFERENCES marketplace_listings(id),
    description_saved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
