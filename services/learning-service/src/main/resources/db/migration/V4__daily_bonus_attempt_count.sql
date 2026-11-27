-- =========================================================================
-- U6: a napi bónusz pontjóváírásának retry-számlálója (TERV 5.4, 7.8). A
-- user_daily_progress bónusz-küldését ugyanúgy 3 próbálkozásra (1/5/15 perc)
-- korlátozzuk, mint az exercise_attempts pontjóváírását (V3). A számlálót a
-- poller olvassa (daily_bonus_attempt_count < 3), ezért kell az oszlop.
-- Additív migráció; a ddl-auto=validate ezt az új, NOT NULL DEFAULT 0 oszlopot
-- várja. A V1 baseline már tartalmazza a version BIGINT NOT NULL DEFAULT 0
-- oszlopot (@Version) és a bónusz-mezőket, azokat nem módosítjuk.
-- =========================================================================

ALTER TABLE user_daily_progress
    ADD COLUMN daily_bonus_attempt_count INT NOT NULL DEFAULT 0;
