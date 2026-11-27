-- =========================================================================
-- Learning Service V2: egy aktív nyelv / felhasználó invariáns adatbázis
-- szintű kikényszerítése (TERV B – U1 backlog). Parciális unique index:
-- felhasználónként legfeljebb egy aktív beiratkozás létezhet. A konkurens
-- aktiválás így DataIntegrityViolationException-t vált ki, amit az
-- EnrollmentService 409 (ConflictException) válasszá fordít.
-- =========================================================================

CREATE UNIQUE INDEX uq_active_enrollment_per_user
    ON user_language_enrollments (user_id)
    WHERE active;
