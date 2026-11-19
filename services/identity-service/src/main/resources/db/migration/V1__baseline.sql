-- =========================================================================
-- Identity Service baseline séma (TERV 6.1).
-- Minden időbélyeg UTC (D17). Enumok szöveges kódként tárolódnak.
-- =========================================================================

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    username      VARCHAR(30)  NOT NULL UNIQUE,
    display_name  VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Kisbetűs keresőindex a felhasználónévre (prefix search).
CREATE INDEX idx_users_username_lower ON users (lower(username));

CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ  NOT NULL,
    revoked_at      TIMESTAMPTZ,
    replaced_by_id  UUID REFERENCES refresh_tokens (id),
    user_agent_hash VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

CREATE TABLE user_stats (
    user_id      UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    total_points BIGINT NOT NULL DEFAULT 0 CHECK (total_points >= 0),
    level        INT    NOT NULL DEFAULT 1 CHECK (level >= 1),
    version      BIGINT NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE point_transactions (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source_type     VARCHAR(40) NOT NULL,
    source_id       VARCHAR(255),
    points          INT NOT NULL CHECK (points > 0),
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_point_transactions_user_id ON point_transactions (user_id);
