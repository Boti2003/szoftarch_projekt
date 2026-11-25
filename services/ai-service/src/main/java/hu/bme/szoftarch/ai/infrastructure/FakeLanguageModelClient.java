package hu.bme.szoftarch.ai.infrastructure;

import hu.bme.szoftarch.ai.domain.ConversationTurn;
import hu.bme.szoftarch.ai.domain.LanguageModelClient;
import hu.bme.szoftarch.ai.domain.MessageRole;
import hu.bme.szoftarch.ai.domain.ScenarioConfig;
import hu.bme.szoftarch.ai.domain.ScenarioEvaluation;
import hu.bme.szoftarch.ai.domain.TranslationEvaluation;
import hu.bme.szoftarch.ai.domain.TranslationRequest;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
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

    @Override
    public String continueScenario(ScenarioConfig config, List<ConversationTurn> history) {
        long userTurns = history.stream().filter(t -> t.role() == MessageRole.USER).count();
        String lastUser = history.stream()
                .filter(t -> t.role() == MessageRole.USER)
                .reduce((first, second) -> second)
                .map(ConversationTurn::content)
                .orElse("");

        // Determinisztikus, a helyzethez illő válasz a fordulószám alapján.
        // A valódi adapter ezt a külső modell válaszára cseréli.
        String role = config.role() == null || config.role().isBlank() ? "partner" : config.role();
        return switch ((int) Math.min(userTurns, 3)) {
            case 0, 1 -> "(" + role + ") Rendben, értem. Mesélj még erről: \"" + shorten(lastUser) + "\"?";
            case 2 -> "(" + role + ") Jó. És mit szeretnél még tenni ebben a helyzetben?";
            default -> "(" + role + ") Köszönöm, azt hiszem minden megvan, amire szükség volt.";
        };
    }

    @Override
    public ScenarioEvaluation evaluateScenario(ScenarioConfig config, List<ConversationTurn> history) {
        List<String> userMessages = history.stream()
                .filter(t -> t.role() == MessageRole.USER)
                .map(ConversationTurn::content)
                .toList();

        // Érthetőség: az üzenetek átlagos hossza (hosszabb, tagoltabb válasz jobb),
        // 3–20 szó között lineárisan skálázva.
        double avgWords = userMessages.isEmpty() ? 0
                : userMessages.stream().mapToInt(m -> m.trim().split("\\s+").length).average().orElse(0);
        double comprehensibility = clamp((avgWords - 3.0) / (20.0 - 3.0));

        // Nyelvhelyesség: a célnyelvi karakterkészlet aránya (betűk/szóközök),
        // egyszerű proxy a "tisztán megírt" szövegre.
        double grammar = userMessages.isEmpty() ? 0 : userMessages.stream()
                .mapToDouble(this::alphaRatio).average().orElse(0);

        // Feladatteljesítés: a cél kulcsszavai mennyire jelennek meg a beszélgetésben.
        double taskCompletion = goalCoverage(config, userMessages);

        double overall = (comprehensibility + grammar + taskCompletion) / 3.0;
        String feedback = overall >= ScenarioEvaluation.PASS_THRESHOLD
                ? "Szép munka, a beszélgetés célját elérted."
                : "A beszélgetés rövid vagy hiányos maradt, próbálj részletesebben válaszolni.";

        return new ScenarioEvaluation(
                round2(comprehensibility), round2(grammar), round2(taskCompletion), feedback);
    }

    private double goalCoverage(ScenarioConfig config, List<String> userMessages) {
        if (config.goal() == null || config.goal().isBlank()) {
            return 0.5;
        }
        Set<String> goalTokens = tokenize(config.goal());
        if (goalTokens.isEmpty()) {
            return 0.5;
        }
        Set<String> spoken = new LinkedHashSet<>();
        userMessages.forEach(m -> spoken.addAll(tokenize(m)));

        long covered = goalTokens.stream().filter(spoken::contains).count();
        return (double) covered / goalTokens.size();
    }

    private double alphaRatio(String text) {
        if (text.isBlank()) {
            return 0.0;
        }
        long relevant = text.chars().filter(c -> Character.isLetter(c) || Character.isWhitespace(c)).count();
        return (double) relevant / text.length();
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private String shorten(String text) {
        String trimmed = text.trim();
        return trimmed.length() <= 40 ? trimmed : trimmed.substring(0, 40) + "…";
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
