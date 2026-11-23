package hu.bme.szoftarch.ai.infrastructure;

import hu.bme.szoftarch.ai.domain.TranslationEvaluation;
import hu.bme.szoftarch.ai.domain.TranslationRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FakeLanguageModelClientTest {

    private final FakeLanguageModelClient client = new FakeLanguageModelClient();

    @Test
    void identicalTranslationScoresFull() {
        var request = new TranslationRequest(
                "A kutya fut.", "en", "The dog runs.",
                List.of("The dog runs."), List.of());

        TranslationEvaluation result = client.evaluateTranslation(request);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.accepted()).isTrue();
    }

    @Test
    void ignoresAccentsAndPunctuation() {
        var request = new TranslationRequest(
                "Jó reggelt", "en", "good morning",
                List.of("Good morning!"), List.of());

        TranslationEvaluation result = client.evaluateTranslation(request);

        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void partialMatchBelowThreshold() {
        var request = new TranslationRequest(
                "A macska alszik.", "en", "The cat",
                List.of("The cat is sleeping."), List.of());

        TranslationEvaluation result = client.evaluateTranslation(request);

        // 2 common tokens ("the","cat") out of a 4-element union -> 0.5
        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.accepted()).isFalse();
    }

    @Test
    void picksBestMatchingReference() {
        var request = new TranslationRequest(
                "Köszönöm", "en", "thank you",
                List.of("thanks", "thank you very much", "thank you"), List.of());

        TranslationEvaluation result = client.evaluateTranslation(request);

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.accepted()).isTrue();
    }

    @Test
    void neutralScoreWithoutReference() {
        var request = new TranslationRequest(
                "Bármi", "en", "anything", List.of(), List.of());

        TranslationEvaluation result = client.evaluateTranslation(request);

        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.accepted()).isFalse();
    }

    @Test
    void deterministicForSameInput() {
        var request = new TranslationRequest(
                "Teszt", "en", "a partial test here",
                List.of("this is the expected test"), List.of());

        double first = client.evaluateTranslation(request).score();
        double second = client.evaluateTranslation(request).score();

        assertThat(first).isEqualTo(second);
    }
}
