package hu.bme.szoftarch.learning.support;

/**
 * Teszt-only mintaadatok: minden feladattípushoz érvényes payload és (ahol van)
 * solution JSON. A solution-szivárgást fedő tesztek ezekből a solution
 * JSON-okból veszik azokat a JSON-kulcsokat/értékeket, amelyeknek SOHA nem
 * szabad megjelenniük a prezentációs válaszban.
 */
public final class ExerciseSamples {

    private ExerciseSamples() {
    }

    public record Sample(String type, String payloadJson, String solutionJson) {
    }

    public static final Sample MULTIPLE_CHOICE = new Sample(
            "MULTIPLE_CHOICE",
            """
            {"multiSelect": false, "options": [
                {"id": "a", "text": "cat"},
                {"id": "b", "text": "dog"},
                {"id": "c", "text": "fish"}
            ]}""",
            "{\"correctOptionIds\": [\"b\"]}");

    public static final Sample TRUE_FALSE = new Sample(
            "TRUE_FALSE",
            "{\"statement\": \"The sky is green.\"}",
            "{\"answer\": false}");

    public static final Sample MATCHING = new Sample(
            "MATCHING",
            """
            {"leftItems": [{"id": "l1", "text": "one"}, {"id": "l2", "text": "two"}],
             "rightItems": [{"id": "r1", "text": "egy"}, {"id": "r2", "text": "ketto"}]}""",
            """
            {"pairs": [{"leftId": "l1", "rightId": "r1"}, {"leftId": "l2", "rightId": "r2"}]}""");

    public static final Sample ODD_ONE_OUT = new Sample(
            "ODD_ONE_OUT",
            """
            {"items": [{"id": "i1", "text": "apple"}, {"id": "i2", "text": "banana"},
                       {"id": "i3", "text": "car"}]}""",
            "{\"oddItemId\": \"i3\"}");

    public static final Sample ERROR_FINDING = new Sample(
            "ERROR_FINDING",
            """
            {"tokens": [{"index": 0, "text": "She"}, {"index": 1, "text": "go"},
                        {"index": 2, "text": "home"}]}""",
            "{\"incorrectTokenIndexes\": [1]}");

    public static final Sample TRANSLATION = new Sample(
            "TRANSLATION",
            """
            {"sourceText": "Jo reggelt", "targetLanguageCode": "en",
             "rubric": ["grammar", "word choice"]}""",
            null);

    public static final Sample AI_SCENARIO = new Sample(
            "AI_SCENARIO",
            """
            {"role": "waiter", "situation": "Ordering coffee", "goals": ["order a drink"],
             "targetLanguageCode": "en", "openingMessage": "Welcome! What would you like?"}""",
            null);

    public static final Sample[] ALL = {
            MULTIPLE_CHOICE, TRUE_FALSE, MATCHING, ODD_ONE_OUT, ERROR_FINDING, TRANSLATION, AI_SCENARIO
    };
}
