package hu.bme.szoftarch.learning.domain.exercise;

/**
 * A felhasználó kakukktojás válasza (U3): a kilógónak jelölt elem ID-ja.
 *
 * @param selectedItemId a kiválasztott {@code Item.id}; lehet {@code null} (üres válasz)
 */
public record OddOneOutAnswer(String selectedItemId) implements UserAnswer {
}
