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
