-- =========================================================================
-- U8: egy beküldés összekötése az AI-munkamenettel és a szerveroldalon
-- kiértékelt pont tárolása (TERV 7.7). A párbeszédes (AI_SCENARIO) feladat
-- munkamenet-azonosítóját, körszámát és a kiértékelt pontot tároljuk; a
-- point-award ezt a kiértékelt pontot küldi (ha van), nem a fix maxPoints-ot.
-- Additív migráció; a ddl-auto=validate ezeket az új oszlopokat várja.
-- =========================================================================

ALTER TABLE exercise_attempts
    ADD COLUMN ai_session_id UUID;

ALTER TABLE exercise_attempts
    ADD COLUMN turn_count INT NOT NULL DEFAULT 0;

ALTER TABLE exercise_attempts
    ADD COLUMN awarded_points INT;

CREATE INDEX idx_attempts_ai_session ON exercise_attempts (ai_session_id);
