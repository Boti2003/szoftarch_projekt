-- =========================================================================
-- Social Service V2 — additív bővítés a V1 baseline fölé (NEM módosítja V1-et).
-- Cél: a duel státusz enum teljes készletének DB-szintű kikényszerítése,
-- a résztvevő státusz enum kikényszerítése, valamint a notification / expiry
-- lekérdezéseket támogató indexek. Semmilyen oszlop nem kerül eldobásra vagy
-- típusváltásra; minden időbélyeg UTC.
-- Cél-adatbázis: PostgreSQL.
-- =========================================================================

-- A duels.status a teljes állapotgép-enumot veheti fel (DuelStatus):
-- PENDING | ACCEPTED | IN_PROGRESS | COMPLETED | REJECTED | EXPIRED.
ALTER TABLE duels
    ADD CONSTRAINT chk_duels_status
    CHECK (status IN ('PENDING', 'ACCEPTED', 'IN_PROGRESS', 'COMPLETED', 'REJECTED', 'EXPIRED'));

-- A duel_participants.status a résztvevő-enumot veheti fel (ParticipantStatus):
-- NOT_STARTED | IN_PROGRESS | COMPLETED.
ALTER TABLE duel_participants
    ADD CONSTRAINT chk_duel_participants_status
    CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED'));

-- Státusz szerinti szűrés a notification összegzéshez (actionable párbajok)
-- és a jövőbeli expiry-söpréshez.
CREATE INDEX idx_duels_status ON duels (status);

-- Lejárati idő szerinti indexelés a (jövőbeli) expiry-söpréshez és
-- az ablakhatár-lekérdezésekhez.
CREATE INDEX idx_duels_expires_at ON duels (expires_at);

-- Parciális index a notification lekérdezéshez: csak a felhasználó számára
-- "actionable" párbajok (PENDING kihívás elfogadásra / IN_PROGRESS folyamatban)
-- kerülnek az indexbe, így a 15 mp-es polling count-lekérdezések szűken,
-- opponent_id szerint szűrve kiszolgálhatók. A lezárt/elutasított/lejárt
-- párbajok kimaradnak az indexből, ami kicsiben és írás-barát módon tartja.
CREATE INDEX idx_duels_actionable
    ON duels (opponent_id, status)
    WHERE status IN ('PENDING', 'IN_PROGRESS');
