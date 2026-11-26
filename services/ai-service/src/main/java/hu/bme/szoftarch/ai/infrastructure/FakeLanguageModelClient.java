package hu.bme.szoftarch.ai.infrastructure;

import hu.bme.szoftarch.ai.domain.LanguageModelClient;
import hu.bme.szoftarch.ai.domain.TranslationEvaluation;
import hu.bme.szoftarch.ai.domain.TranslationRequest;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Determinisztikus mock nyelvi modell. Kulcs és külső hívás nélkül fut,
 * ezért fejlesztéshez és automata teszthez használható.
 *
 * <p>A fordítás pontszáma a tanuló szövege és a legjobban illeszkedő
 * referencia-fordítás közötti token-átfedés (Jaccard-hasonlóság) alapján
 * áll elő. Ugyanarra a bemenetre mindig ugyanazt adja, így kiszámítható
 * és tesztelhető.
 */
public class FakeLanguageModelClient implements LanguageModelClient {

    @Override
    public TranslationEvaluation evaluateTranslation(TranslationRequest request) {
        Set<String> learnerTokens = tokenize(request.learnerTranslation());

        double bestScore = request.referenceTranslations().stream()
                .map(this::tokenize)
                .mapToDouble(reference -> jaccard(learnerTokens, reference))
                .max()
                .orElse(0.0);

        // Referencia hiányában nem tudunk egzakt viszonyítani; semleges,
        // de nem elfogadó pontszámot adunk, hogy a hiányt jelezze.
        if (request.referenceTranslations().isEmpty()) {
            bestScore = 0.5;
        }

        double score = round2(bestScore);
        return TranslationEvaluation.of(score, buildFeedback(score));
    }

    private String buildFeedback(double score) {
        if (score >= 0.9) {
            return "Kiváló fordítás, szinte teljesen egyezik az elvárttal.";
        }
        if (score >= TranslationEvaluation.ACCEPTANCE_THRESHOLD) {
            return "Jó fordítás, a lényeg átjött. Néhány szó eltér a referenciától.";
        }
        if (score >= 0.4) {
            return "Részben helyes, de több kulcsszó hiányzik vagy eltér.";
        }
        return "A fordítás jelentősen eltér az elvárttól, próbáld újra.";
    }

    /**
     * Ékezet- és írásjel-független, kisbetűs tokenhalmaz.
     */
    private Set<String> tokenize(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ");
        return new LinkedHashSet<>(Arrays.asList(normalized.trim().split("\\s+")));
    }

    private double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        Set<String> intersection = new LinkedHashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new LinkedHashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
