-- =========================================================================
-- AI Service baseline séma (TERV 6.4).
-- Nem tulajdonol pontot vagy progresst. Megőrzés: legfeljebb 30 nap.
-- Minden időbélyeg UTC (D17).
-- =========================================================================

CREATE TABLE ai_sessions (
    id                   UUID PRIMARY KEY,
    learning_attempt_id  UUID NOT NULL UNIQUE,           -- Learning attempt UUID
    user_id              UUID NOT NULL,
    exercise_id          UUID NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    provider             VARCHAR(40) NOT NULL,
    turn_count           INT NOT NULL DEFAULT 0,
    scenario_config      JSONB,
    final_evaluation     JSONB,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ai_messages (
    id                UUID PRIMARY KEY,
    session_id        UUID NOT NULL REFERENCES ai_sessions (id) ON DELETE CASCADE,
    sequence_no       INT NOT NULL,
    role              VARCHAR(20) NOT NULL,               -- SYSTEM | USER | ASSISTANT
    content           TEXT NOT NULL,
    provider_metadata JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (session_id, sequence_no)
);
