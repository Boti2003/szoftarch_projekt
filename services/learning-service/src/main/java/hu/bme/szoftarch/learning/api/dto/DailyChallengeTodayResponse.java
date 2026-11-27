package hu.bme.szoftarch.learning.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import hu.bme.szoftarch.learning.domain.model.ProgressStatus;

/**
 * A mai napi kihívás a {@code GET /api/v1/daily-challenges/today} válaszában
 * (TERV 5.4, 7.4): a (budapesti üzleti) nap feladatsora az adott nyelven + a
 * bejelentkezett felhasználó saját státusza.
 *
 * <p><strong>Kritikus (TERV 5.2):</strong> a feladatok prezentációja a
 * {@code payload}-ból származik, a helyes megoldás NÉLKÜL (az U2
 * {@link ExercisePresentationResponse} leképezés).</p>
 *
 * @param challengeId a napi kihívás azonosítója
 * @param date        az üzleti nap dátuma (budapesti)
 * @param languageId  a nyelv azonosítója
 * @param exercises   a feladatok prezentációja pozíció szerinti sorrendben,
 *     solution nélkül
 * @param ownStatus   a hívó saját napi státusza
 */
public record DailyChallengeTodayResponse(
        UUID challengeId,
        LocalDate date,
        UUID languageId,
        List<ExercisePresentationResponse> exercises,
        OwnStatus ownStatus) {

    /**
     * A bejelentkezett felhasználó saját napi haladása.
     *
     * @param status        NOT_STARTED / IN_PROGRESS / COMPLETED
     * @param correctCount  a helyes napi válaszok száma
     * @param bonusAwarded  igaz, ha a +20 bónusz már jóváíródott
     */
    public record OwnStatus(ProgressStatus status, int correctCount, boolean bonusAwarded) {
    }
}
