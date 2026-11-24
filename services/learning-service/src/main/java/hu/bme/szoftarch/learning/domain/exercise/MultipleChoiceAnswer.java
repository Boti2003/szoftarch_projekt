package hu.bme.szoftarch.learning.domain.exercise;

import java.util.HashSet;
import java.util.Set;

/**
 * A felhasználó feleletválasztós válasza (U3): a kiválasztott opció-ID-k.
 *
 * @param selectedOptionIds a kiválasztott {@code Option.id} értékek; lehet üres
 */
public record MultipleChoiceAnswer(Set<String> selectedOptionIds) implements UserAnswer {

    public MultipleChoiceAnswer {
        selectedOptionIds = selectedOptionIds == null
                ? Set.of()
                : Set.copyOf(new HashSet<>(selectedOptionIds));
    }
}
