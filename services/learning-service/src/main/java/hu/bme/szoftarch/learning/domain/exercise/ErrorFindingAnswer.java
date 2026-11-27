package hu.bme.szoftarch.learning.domain.exercise;

import java.util.HashSet;
import java.util.Set;

/**
 * A felhasználó hibakereső válasza (U3): a hibásnak jelölt tokenindexek.
 *
 * @param markedTokenIndexes a hibásnak jelölt indexek halmaza; lehet üres
 */
public record ErrorFindingAnswer(Set<Integer> markedTokenIndexes) implements UserAnswer {

    public ErrorFindingAnswer {
        markedTokenIndexes = markedTokenIndexes == null
                ? Set.of()
                : Set.copyOf(new HashSet<>(markedTokenIndexes));
    }
}
