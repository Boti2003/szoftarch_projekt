package hu.bme.szoftarch.ai.infrastructure.openai;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * A nyelvi modelltől elvárt szituáció záró értékelés kimenete. A három
 * részpontszám 0–1 közötti; a Bean Validation annotációk alkotják a sémát.
 */
public record ScenarioEvaluationOutput(
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double comprehensibility,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double grammar,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double taskCompletion,
        @NotNull String feedback) {
}
