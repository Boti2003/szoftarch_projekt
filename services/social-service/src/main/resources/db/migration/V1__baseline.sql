-- =========================================================================
-- Social Service baseline séma (TERV 6.3).
-- Minden időbélyeg UTC (D17). Más domain entitására csak UUID hivatkozás.
-- =========================================================================

CREATE TABLE friend_requests (
    id          UUID PRIMARY KEY,
    sender_id   UUID NOT NULL,
    receiver_id UUID NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | ACCEPTED | REJECTED
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_friend_request_distinct CHECK (sender_id <> receiver_id)
);

-- Egy aktív (PENDING) kérés páronként; az irányt is figyeljük.
CREATE UNIQUE INDEX uq_friend_request_pending
    ON friend_requests (sender_id, receiver_id)
    WHERE status = 'PENDING';

CREATE TABLE friendships (
    id           UUID PRIMARY KEY,
    user_low_id  UUID NOT NULL,                          -- rendezett UUID-pár
    user_high_id UUID NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_friendship_ordered CHECK (user_low_id < user_high_id),
    UNIQUE (user_low_id, user_high_id)
);

CREATE TABLE duels (
    id              UUID PRIMARY KEY,
    challenger_id   UUID NOT NULL,
    opponent_id     UUID NOT NULL,
    language_id     UUID NOT NULL,
    learning_set_id UUID,                                -- Learning duel_set UUID
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at      TIMESTAMPTZ,
    winner_id       UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_duels_challenger ON duels (challenger_id);
CREATE INDEX idx_duels_opponent   ON duels (opponent_id);

CREATE TABLE duel_participants (
    duel_id        UUID NOT NULL REFERENCES duels (id) ON DELETE CASCADE,
    user_id        UUID NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    score          INT NOT NULL DEFAULT 0,
    answered_count INT NOT NULL DEFAULT 0,
    started_at     TIMESTAMPTZ,
    completed_at   TIMESTAMPTZ,
    version        BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (duel_id, user_id)
);
