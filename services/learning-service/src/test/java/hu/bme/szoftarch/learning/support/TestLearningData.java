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
