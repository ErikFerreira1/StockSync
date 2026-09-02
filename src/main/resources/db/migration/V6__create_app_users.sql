CREATE TABLE app_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_users_username UNIQUE (username),
    CONSTRAINT ck_app_users_role CHECK (role IN ('ADMIN', 'OPERATOR', 'VIEWER'))
);
