package hu.bme.szoftarch.learning.support;

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
        jdbc.update("INSERT INTO exercises (id, lesson_id, type, difficulty, order_index, prompt, payload, "
                        + "solution, max_points, required_for_completion, duel_eligible, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, lessonId, type, difficulty, orderIndex, prompt, payloadJson, solutionJson,
                maxPoints, requiredForCompletion, duelEligible, enabled);
        return id;
    }

    public void progressCompleted(UUID userId, UUID lessonId) {
        insertProgress(userId, lessonId, "COMPLETED");
    }

    public void progressInProgress(UUID userId, UUID lessonId) {
        insertProgress(userId, lessonId, "IN_PROGRESS");
    }

    private void insertProgress(UUID userId, UUID lessonId, String status) {
        jdbc.update("INSERT INTO lesson_progress (user_id, lesson_id, status, version) VALUES (?, ?, ?, 0)",
                userId, lessonId, status);
    }
}
