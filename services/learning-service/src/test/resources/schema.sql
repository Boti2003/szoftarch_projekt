-- Teszt séma (H2, PostgreSQL mód) az U1 repository/MVC tesztekhez.
-- A Flyway a tesztben ki van kapcsolva; az éles séma a V1__baseline.sql.
-- Csak az U1 által olvasott/írt táblák szerepelnek itt.

CREATE TABLE IF NOT EXISTS languages (
    id         UUID PRIMARY KEY,
    code       VARCHAR(10)  NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL,
    enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS user_language_enrollments (
    user_id     UUID NOT NULL,
    language_id UUID NOT NULL REFERENCES languages (id),
    active      BOOLEAN NOT NULL DEFAULT FALSE,
    started_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, language_id)
);

CREATE TABLE IF NOT EXISTS lessons (
    id              UUID PRIMARY KEY,
    language_id     UUID NOT NULL REFERENCES languages (id),
    order_index     INT  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    theory_markdown TEXT,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (language_id, order_index)
);

CREATE TABLE IF NOT EXISTS lesson_progress (
    user_id          UUID NOT NULL,
    lesson_id        UUID NOT NULL REFERENCES lessons (id),
    status           VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    best_score_ratio NUMERIC(5,4),
    completed_at     TIMESTAMP WITH TIME ZONE,
    version          BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, lesson_id)
);

-- U2: exercises. A payload/solution JSONB-t a JPA konverter stringként kezeli,
-- ezért a teszt-sémában szöveges oszlop (H2 nem támogatja a natív jsonb típust).
CREATE TABLE IF NOT EXISTS exercises (
    id                      UUID PRIMARY KEY,
    lesson_id               UUID NOT NULL REFERENCES lessons (id),
    type                    VARCHAR(30) NOT NULL,
    difficulty              VARCHAR(10) NOT NULL,
    order_index             INT NOT NULL,
    prompt                  TEXT NOT NULL,
    payload                 TEXT NOT NULL,
    solution                TEXT,
    max_points              INT NOT NULL,
    required_for_completion BOOLEAN NOT NULL DEFAULT TRUE,
    duel_eligible           BOOLEAN NOT NULL DEFAULT FALSE,
    enabled                 BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (lesson_id, order_index)
);

-- U4: exercise_attempts. A JSONB answer-t a JPA konverter stringként kezeli,
-- ezért a teszt-sémában TEXT (H2 nem támogatja a natív jsonb típust).
CREATE TABLE IF NOT EXISTS exercise_attempts (
    id                    UUID PRIMARY KEY,
    user_id               UUID NOT NULL,
    exercise_id           UUID NOT NULL REFERENCES exercises (id),
    context               VARCHAR(20) NOT NULL,
    context_id            UUID,
    answer                TEXT,
    score_ratio           NUMERIC(5,4),
    correct               BOOLEAN,
    feedback              TEXT,
    point_award_status    VARCHAR(20) NOT NULL DEFAULT 'NOT_APPLICABLE',
    point_idempotency_key VARCHAR(255),
    point_attempt_count   INT NOT NULL DEFAULT 0,
    next_retry_at         TIMESTAMP WITH TIME ZONE,
    submitted_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    idempotency_key       VARCHAR(255) NOT NULL UNIQUE
);

-- U6: napi kihívás táblák (TERV 6.2). A daily táblák nem tartalmaznak JSONB-t,
-- ezért H2-kompatibilis típusokkal, a V1 + V4 sémát tükrözve.
CREATE TABLE IF NOT EXISTS daily_challenges (
    id             UUID PRIMARY KEY,
    challenge_date DATE NOT NULL,
    language_id    UUID NOT NULL REFERENCES languages (id),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (challenge_date, language_id)
);

CREATE TABLE IF NOT EXISTS daily_challenge_exercises (
    challenge_id UUID NOT NULL REFERENCES daily_challenges (id) ON DELETE CASCADE,
    position     INT  NOT NULL,
    exercise_id  UUID NOT NULL REFERENCES exercises (id),
    PRIMARY KEY (challenge_id, position)
);

CREATE TABLE IF NOT EXISTS user_daily_progress (
    user_id                   UUID NOT NULL,
    challenge_id              UUID NOT NULL REFERENCES daily_challenges (id),
    status                    VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    correct_count             INT NOT NULL DEFAULT 0,
    bonus_awarded             BOOLEAN NOT NULL DEFAULT FALSE,
    bonus_award_status        VARCHAR(20) NOT NULL DEFAULT 'NOT_APPLICABLE',
    bonus_idempotency_key     VARCHAR(255),
    daily_bonus_attempt_count INT NOT NULL DEFAULT 0,
    next_retry_at             TIMESTAMP WITH TIME ZONE,
    completed_at              TIMESTAMP WITH TIME ZONE,
    version                   BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, challenge_id)
);

-- U7: párbaj duel-set táblák (TERV 6.2). A duel_answers JSONB answer-je a JPA
-- @JdbcTypeCode(SqlTypes.JSON) mappinggel megy; a teszt-sémában TEXT (H2 nem
-- támogatja a natív jsonb típust), a V1 sémát tükrözve.
CREATE TABLE IF NOT EXISTS duel_sets (
    id               UUID PRIMARY KEY,
    external_duel_id UUID NOT NULL UNIQUE,
    language_id      UUID NOT NULL REFERENCES languages (id),
    expires_at       TIMESTAMP WITH TIME ZONE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS duel_set_exercises (
    duel_set_id UUID NOT NULL REFERENCES duel_sets (id) ON DELETE CASCADE,
    position    INT  NOT NULL,
    exercise_id UUID NOT NULL REFERENCES exercises (id),
    PRIMARY KEY (duel_set_id, position)
);

CREATE TABLE IF NOT EXISTS duel_answers (
    duel_set_id  UUID NOT NULL REFERENCES duel_sets (id) ON DELETE CASCADE,
    user_id      UUID NOT NULL,
    position     INT  NOT NULL,
    answer       TEXT,
    score        INT,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (duel_set_id, user_id, position)
);
