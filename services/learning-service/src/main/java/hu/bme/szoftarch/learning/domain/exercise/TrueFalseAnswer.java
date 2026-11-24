package hu.bme.szoftarch.learning.domain.exercise;

/**
 * A felhasználó igaz-hamis válasza (U3).
 *
 * @param answer a megadott érték: {@code true} ha az állítást igaznak jelölte
 */
public record TrueFalseAnswer(boolean answer) implements UserAnswer {
}
