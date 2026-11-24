package hu.bme.szoftarch.learning.api.dto;

import java.util.List;
import java.util.UUID;

import hu.bme.szoftarch.learning.domain.model.Difficulty;
import hu.bme.szoftarch.learning.domain.model.ExerciseType;

/**
 * Egy feladat teljes PREZENTÁCIÓJA a {@code GET /api/v1/exercises/{exerciseId}}
 * válaszában (TERV 7.4). Típusfüggő: a {@code content} a feladattípus szerinti
 * prezentációs adatot tartalmazza (opciók, tokenek, párosítandó elemek,
 * scenario kezdőüzenet stb.), kizárólag a {@code payload}-ból származtatva.
 *
 * <p><strong>Kritikus (TERV 5.2):</strong> a helyes megoldás ({@code solution})
 * egyetlen típusnál sem kerül a válaszba. A {@code content} ágak egyikében sincs
 * solution mező.</p>
 */
public record ExercisePresentationResponse(
        UUID exerciseId,
        ExerciseType type,
        Difficulty difficulty,
        String prompt,
        int maxPoints,
        boolean requiredForCompletion,
        boolean duelEligible,
        ExerciseContent content) {

    /**
     * A típusfüggő prezentációs tartalom közös őse. A {@code type} diszkriminátor
     * alapján a frontend a megfelelő ágat rendereli. Egyik permit-ált
     * leszármazott sem tartalmaz megoldásmezőt.
     */
    public sealed interface ExerciseContent permits
            MultipleChoiceContent,
            TrueFalseContent,
            MatchingContent,
            OddOneOutContent,
            ErrorFindingContent,
            TranslationContent,
            ScenarioContent {
    }

    /**
     * Feleletválasztós prezentáció: az opciók (megoldásjelölés nélkül).
     */
    public record MultipleChoiceContent(
            boolean multiSelect,
            List<Option> options) implements ExerciseContent {

        public record Option(String id, String text) {
        }
    }

    /**
     * Igaz-hamis prezentáció: a megítélendő állítás (megoldás nélkül).
     */
    public record TrueFalseContent(String statement) implements ExerciseContent {
    }

    /**
     * Párosítós prezentáció: a bal és jobb elemek (helyes párok nélkül).
     */
    public record MatchingContent(
            List<Item> leftItems,
            List<Item> rightItems) implements ExerciseContent {

        public record Item(String id, String text) {
        }
    }

    /**
     * Kakukktojás prezentáció: az elemek (a kilógó elem megjelölése nélkül).
     */
    public record OddOneOutContent(List<Item> items) implements ExerciseContent {

        public record Item(String id, String text) {
        }
    }

    /**
     * Hibakereső prezentáció: az indexelt tokenek (a hibás indexek nélkül).
     */
    public record ErrorFindingContent(List<Token> tokens) implements ExerciseContent {

        public record Token(int index, String text) {
        }
    }

    /**
     * Fordítós prezentáció: a forrásszöveg, célnyelv és értékelési szempontok.
     */
    public record TranslationContent(
            String sourceText,
            String targetLanguageCode,
            List<String> rubric) implements ExerciseContent {
    }

    /**
     * AI-szituáció prezentáció: szerep, helyzet, célok, célnyelv, kezdőüzenet.
     */
    public record ScenarioContent(
            String role,
            String situation,
            List<String> goals,
            String targetLanguageCode,
            String openingMessage) implements ExerciseContent {
    }
}
