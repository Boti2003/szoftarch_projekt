-- =========================================================================
-- Learning Service baseline séma (TERV 6.2).
-- Minden időbélyeg UTC (D17). Más domain entitására csak UUID hivatkozás.
-- =========================================================================

CREATE TABLE languages (
    id         UUID PRIMARY KEY,
    code       VARCHAR(10)  NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL,
    enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_language_enrollments (
    user_id     UUID NOT NULL,                         -- külső domain-azonosító (Identity)
    language_id UUID NOT NULL REFERENCES languages (id),
    active      BOOLEAN NOT NULL DEFAULT FALSE,
    started_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, language_id)
);

CREATE TABLE lessons (
    id              UUID PRIMARY KEY,
    language_id     UUID NOT NULL REFERENCES languages (id),
    order_index     INT  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    theory_markdown TEXT,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (language_id, order_index)
);

CREATE TABLE exercises (
    id                      UUID PRIMARY KEY,
    lesson_id               UUID NOT NULL REFERENCES lessons (id),
    type                    VARCHAR(30) NOT NULL,
    difficulty              VARCHAR(10) NOT NULL,
    order_index             INT NOT NULL,
    prompt                  TEXT NOT NULL,
    payload                 JSONB NOT NULL,
    solution                JSONB,
    max_points              INT NOT NULL,
    required_for_completion BOOLEAN NOT NULL DEFAULT TRUE,
    duel_eligible           BOOLEAN NOT NULL DEFAULT FALSE,
    enabled                 BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (lesson_id, order_index)
);

CREATE TABLE exercise_attempts (
    id                    UUID PRIMARY KEY,
    user_id               UUID NOT NULL,
    exercise_id           UUID NOT NULL REFERENCES exercises (id),
    context               VARCHAR(20) NOT NULL,          -- LESSON | DAILY
    context_id            UUID,
    answer                JSONB,
    score_ratio           NUMERIC(5,4),
    correct               BOOLEAN,
    feedback              TEXT,
    point_award_status    VARCHAR(20) NOT NULL DEFAULT 'NOT_APPLICABLE',
    point_idempotency_key VARCHAR(255),
    next_retry_at         TIMESTAMPTZ,
    submitted_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    idempotency_key       VARCHAR(255) NOT NULL UNIQUE
);

CREATE INDEX idx_attempts_user_exercise ON exercise_attempts (user_id, exercise_id);

CREATE TABLE lesson_progress (
    user_id          UUID NOT NULL,
    lesson_id        UUID NOT NULL REFERENCES lessons (id),
    status           VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    best_score_ratio NUMERIC(5,4),
    completed_at     TIMESTAMPTZ,
    version          BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, lesson_id)
);

CREATE TABLE daily_challenges (
    id             UUID PRIMARY KEY,
    challenge_date DATE NOT NULL,
    language_id    UUID NOT NULL REFERENCES languages (id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (challenge_date, language_id)
);

CREATE TABLE daily_challenge_exercises (
    challenge_id UUID NOT NULL REFERENCES daily_challenges (id) ON DELETE CASCADE,
    position     INT  NOT NULL,
    exercise_id  UUID NOT NULL REFERENCES exercises (id),
    PRIMARY KEY (challenge_id, position)
);

CREATE TABLE user_daily_progress (
    user_id              UUID NOT NULL,
    challenge_id         UUID NOT NULL REFERENCES daily_challenges (id),
    status               VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    correct_count        INT NOT NULL DEFAULT 0,
    bonus_awarded        BOOLEAN NOT NULL DEFAULT FALSE,
    bonus_award_status   VARCHAR(20) NOT NULL DEFAULT 'NOT_APPLICABLE',
    bonus_idempotency_key VARCHAR(255),
    next_retry_at        TIMESTAMPTZ,
    completed_at         TIMESTAMPTZ,
    version              BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, challenge_id)
);

CREATE TABLE duel_sets (
    id               UUID PRIMARY KEY,
    external_duel_id UUID NOT NULL UNIQUE,              -- Social duel UUID
    language_id      UUID NOT NULL REFERENCES languages (id),
    expires_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE duel_set_exercises (
    duel_set_id UUID NOT NULL REFERENCES duel_sets (id) ON DELETE CASCADE,
    position    INT  NOT NULL,
    exercise_id UUID NOT NULL REFERENCES exercises (id),
    PRIMARY KEY (duel_set_id, position)
);

CREATE TABLE duel_answers (
    duel_set_id  UUID NOT NULL REFERENCES duel_sets (id) ON DELETE CASCADE,
    user_id      UUID NOT NULL,
    position     INT  NOT NULL,
    answer       JSONB,
    score        INT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (duel_set_id, user_id, position)
);
