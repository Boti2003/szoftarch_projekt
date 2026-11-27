-- =========================================================================
-- A hívó (Learning) a session indításakor csak a config öt mezőjét küldi,
-- belső azonosítót nem. Ezért a learning_attempt_id / user_id / exercise_id
-- opcionálissá válik, és a learning_attempt_id egyediség-megszorítása törlődik.
-- =========================================================================

ALTER TABLE ai_sessions ALTER COLUMN learning_attempt_id DROP NOT NULL;
ALTER TABLE ai_sessions ALTER COLUMN user_id             DROP NOT NULL;
ALTER TABLE ai_sessions ALTER COLUMN exercise_id         DROP NOT NULL;

ALTER TABLE ai_sessions DROP CONSTRAINT IF EXISTS ai_sessions_learning_attempt_id_key;
