package hu.bme.szoftarch.learning.support;

import java.sql.Types;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
/**
 * Teszt-only segéd a learning táblák feltöltéséhez és ürítéséhez JDBC-vel.
 * Csak a teszt classpathon létezik; éles kódba nem kerül.
 */
@Component
public class TestLearningData {

    private final JdbcTemplate jdbc;

    public TestLearningData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void deleteAll() {
        jdbc.update("DELETE FROM duel_answers");
        jdbc.update("DELETE FROM duel_set_exercises");
        jdbc.update("DELETE FROM duel_sets");
        jdbc.update("DELETE FROM exercise_attempts");
        jdbc.update("DELETE FROM user_daily_progress");
        jdbc.update("DELETE FROM daily_challenge_exercises");
        jdbc.update("DELETE FROM daily_challenges");
        jdbc.update("DELETE FROM exercises");
        jdbc.update("DELETE FROM lesson_progress");
        jdbc.update("DELETE FROM user_language_enrollments");
        jdbc.update("DELETE FROM lessons");
        jdbc.update("DELETE FROM languages");
    }

    public UUID language(String code, String name, boolean enabled) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO languages (id, code, name, enabled) VALUES (?, ?, ?, ?)",
                id, code, name, enabled);
        return id;
    }

    public UUID lesson(UUID languageId, int orderIndex, String title) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO lessons (id, language_id, order_index, title, enabled) VALUES (?, ?, ?, ?, ?)",
                id, languageId, orderIndex, title, true);
        return id;
    }

    public UUID lessonWithTheory(UUID languageId, int orderIndex, String title, String theoryMarkdown) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO lessons (id, language_id, order_index, title, theory_markdown, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, languageId, orderIndex, title, theoryMarkdown, true);
        return id;
    }

    /**
     * Beszúr egy feladatot nyers payload/solution JSON-nel. A {@code solutionJson}
     * lehet {@code null} (AI-típusok). A {@code enabled} felülbírálható a
     * disabled/404 teszthez.
     */
    public UUID exercise(UUID lessonId, String type, String difficulty, int orderIndex, String prompt,
            String payloadJson, String solutionJson, int maxPoints, boolean requiredForCompletion,
            boolean duelEligible, boolean enabled) {
        UUID id = UUID.randomUUID();
        // A payload/solution JSONB oszlopok; a JSON stringet Types.OTHER-rel kötjük,
        // hogy a PostgreSQL JDBC driver implicit jsonb-cast nélkül, connection-szintű
        // stringtype=unspecified kompenzáció NÉLKÜL fogadja el (H2 TEXT-re is működik).
        jdbc.update("INSERT INTO exercises (id, lesson_id, type, difficulty, order_index, prompt, payload, "
                        + "solution, max_points, required_for_completion, duel_eligible, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                ps -> {
                    ps.setObject(1, id);
                    ps.setObject(2, lessonId);
                    ps.setString(3, type);
                    ps.setString(4, difficulty);
                    ps.setInt(5, orderIndex);
                    ps.setString(6, prompt);
                    ps.setObject(7, payloadJson, Types.OTHER);
                    ps.setObject(8, solutionJson, Types.OTHER);
                    ps.setInt(9, maxPoints);
                    ps.setBoolean(10, requiredForCompletion);
                    ps.setBoolean(11, duelEligible);
                    ps.setBoolean(12, enabled);
                });
        return id;
    }

    /**
     * Beszúr egy auto-kiértékelhető feladatot a megadott {@code maxPoints} és
     * {@code requiredForCompletion} értékkel, érvényes payload/solution JSON-nel.
     */
    public UUID exerciseScorable(UUID lessonId, String type, int orderIndex, String payloadJson,
            String solutionJson, int maxPoints, boolean requiredForCompletion) {
        return exercise(lessonId, type, "EASY", orderIndex, "Prompt for " + type,
                payloadJson, solutionJson, maxPoints, requiredForCompletion, false, true);
    }

    /**
     * Beszúr egy napi kihívásra alkalmas (duel-eligible), auto-kiértékelhető
     * feladatot érvényes payload/solution JSON-nel (U6 teszt-seed).
     */
    public UUID exerciseDuelEligible(UUID lessonId, String type, int orderIndex, String payloadJson,
            String solutionJson, int maxPoints) {
        return exercise(lessonId, type, "EASY", orderIndex, "Prompt for " + type,
                payloadJson, solutionJson, maxPoints, true, true, true);
    }

    /**
     * Beszúr egy előzetes beküldést a feladatonkénti legjobb pontarányhoz vagy
     * első-helyes detektáláshoz tesztelő esetekhez.
     */
    public UUID attempt(UUID userId, UUID exerciseId, UUID lessonId, String scoreRatio, boolean correct,
            String idempotencyKey, String pointAwardStatus, String pointIdempotencyKey) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exercise_attempts (id, user_id, exercise_id, context, context_id, "
                        + "score_ratio, correct, point_award_status, point_idempotency_key, idempotency_key) "
                        + "VALUES (?, ?, ?, 'LESSON', ?, ?, ?, ?, ?, ?)",
                id, userId, exerciseId, lessonId, scoreRatio, correct, pointAwardStatus,
                pointIdempotencyKey, idempotencyKey);
        return id;
    }

    public void progressCompleted(UUID userId, UUID lessonId) {        insertProgress(userId, lessonId, "COMPLETED");
    }

    public void progressInProgress(UUID userId, UUID lessonId) {
        insertProgress(userId, lessonId, "IN_PROGRESS");
    }

    private void insertProgress(UUID userId, UUID lessonId, String status) {
        jdbc.update("INSERT INTO lesson_progress (user_id, lesson_id, status, version) VALUES (?, ?, ?, 0)",
                userId, lessonId, status);
    }

    /**
     * Egyetlen szöveges oszlopot olvas vissza (pl. JSONB {@code ::text}-ként) a
     * valódi adatbázisból, a JSONB oda-vissza ellenőrzéséhez. Csak teszt célú.
     */
    public String jdbcString(String sql) {
        return jdbc.queryForObject(sql, String.class);
    }
}
