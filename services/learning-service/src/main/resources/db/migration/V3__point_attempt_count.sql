-- =========================================================================
-- U5: a pontjóváírás retry-számlálója (TERV 7.8). A FAILED_RETRYABLE sorok
-- automatikus újrapróbálkozását 3 időzítésre (1/5/15 perc) korlátozzuk; a
-- számlálót a poller olvassa (point_attempt_count < 3), ezért kell oszlop.
-- Additív migráció; a ddl-auto=validate ezt az új, NOT NULL DEFAULT 0 oszlopot várja.
-- =========================================================================

ALTER TABLE exercise_attempts
    ADD COLUMN point_attempt_count INT NOT NULL DEFAULT 0;
