package hu.bme.szoftarch.learning.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import hu.bme.szoftarch.learning.api.NotFoundException;
import hu.bme.szoftarch.learning.api.dto.DuelAnswerResponse;
import hu.bme.szoftarch.learning.api.dto.DuelSetResponse;
import hu.bme.szoftarch.learning.api.dto.ExercisePresentationResponse;
import hu.bme.szoftarch.learning.domain.duel.DuelSetSelection;
import hu.bme.szoftarch.learning.domain.exercise.EvaluationResult;
import hu.bme.szoftarch.learning.domain.exercise.EvaluatorRegistry;
import hu.bme.szoftarch.learning.domain.exercise.ExerciseSolution;
import hu.bme.szoftarch.learning.domain.exercise.NotAutoEvaluableException;
import hu.bme.szoftarch.learning.domain.exercise.UserAnswer;
import hu.bme.szoftarch.learning.domain.model.DuelAnswer;
import hu.bme.szoftarch.learning.domain.model.DuelAnswerId;
import hu.bme.szoftarch.learning.domain.model.DuelSet;
import hu.bme.szoftarch.learning.domain.model.DuelSetExercise;
import hu.bme.szoftarch.learning.domain.model.DuelSetExerciseId;
import hu.bme.szoftarch.learning.domain.model.Exercise;
import hu.bme.szoftarch.learning.domain.model.ExerciseType;
import hu.bme.szoftarch.learning.domain.repository.DuelAnswerRepository;
import hu.bme.szoftarch.learning.domain.repository.DuelSetExerciseRepository;
import hu.bme.szoftarch.learning.domain.repository.DuelSetRepository;
import hu.bme.szoftarch.learning.domain.repository.ExerciseRepository;
import hu.bme.szoftarch.learning.infrastructure.persistence.ExerciseContentCodec;

import tools.jackson.databind.JsonNode;

import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Belső párbaj-feladatkészlet use case-ei (TERV B – U7, 5.5, 7.5). A Social
 * service fogyasztja service-to-service; NINCS JWT user-kontextus, a résztvevő
 * {@code userId}-k a kérés payloadjában érkeznek.
 *
 * <p>Három művelet: a készlet létrehozása (fix 5, duel-eligible, auto feladat,
 * MENTETT sorrenddel), a készlet feladatprezentációinak lekérése (solution
 * nélkül) és egy résztvevő egy válaszának idempotens pontozása.</p>
 *
 * <p><strong>Rögzített készlet (TERV 5.5):</strong> a kiválasztás a
 * {@code duelId}-ből determinisztikus ({@link DuelSetSelection}), de a
 * kiválasztott 5 feladat a {@code duel_set_exercises} táblába MENTŐDIK, és
 * onnan olvasódik vissza — így mindkét résztvevő pontosan ugyanazt az 5
 * feladatot kapja ugyanabban a sorrendben, akkor is, ha a jelölthalmaz később
 * változik.</p>
 *
 * <p><strong>Idempotencia + verseny:</strong> a létrehozás az
 * {@code external_duel_id} (UNIQUE) megszorításra idempotens; az answer a
 * {@code (duel_set_id, user_id, position)} összetett kulcsra. Egyidejű azonos
 * kulcsnál a vesztő tranzakció visszagördül, és a tárolt sort friss
 * tranzakcióban olvassuk vissza — nem 500.</p>
 *
 * <p><strong>Kritikus (TERV 5.5):</strong> a párbaj pontja NEM profilpont — ez
 * a use case SOHA nem hív {@code PointAwardService}/{@code IdentityPointsPort}
 * pontjóváírást. A helyes válasz az {@code exercise.max_points} értéket kapja,
 * a hibás 0-t. A solution sosem kerül a válaszba; egy résztvevő válasza nem
 * szivárogtatja a többiek eredményét.</p>
 */
@Service
public class DuelSetService {

    private final DuelSetRepository duelSetRepository;
    private final DuelSetExerciseRepository duelSetExerciseRepository;
    private final DuelAnswerRepository duelAnswerRepository;
    private final ExerciseRepository exerciseRepository;
    private final ExerciseContentCodec contentCodec;
    private final EvaluatorRegistry evaluatorRegistry;
    private final ExerciseService exerciseService;
    private final DuelSetService self;

    public DuelSetService(DuelSetRepository duelSetRepository,
            DuelSetExerciseRepository duelSetExerciseRepository,
            DuelAnswerRepository duelAnswerRepository,
            ExerciseRepository exerciseRepository,
            ExerciseContentCodec contentCodec,
            EvaluatorRegistry evaluatorRegistry,
            ExerciseService exerciseService,
            @Lazy DuelSetService self) {
        this.duelSetRepository = duelSetRepository;
        this.duelSetExerciseRepository = duelSetExerciseRepository;
        this.duelAnswerRepository = duelAnswerRepository;
        this.exerciseRepository = exerciseRepository;
        this.contentCodec = contentCodec;
        this.evaluatorRegistry = evaluatorRegistry;
        this.exerciseService = exerciseService;
        this.self = self;
    }

    /**
     * Létrehozza (vagy idempotensen visszaadja) a párbaj feladatkészletét.
     * Orkesztrátor: ha az {@code external_duel_id}-re már van készlet, azt adja
     * vissza; különben saját tranzakcióban létrehozza. Egyidejű azonos
     * {@code duelId}-nél a visszagördülő tranzakció után a meglévő készletet friss
     * tranzakcióban olvassuk vissza (nem 500).
     *
     * @param duelId     a Social párbaj UUID-ja (external_duel_id)
     * @param languageId a párbaj nyelve
     * @param expiresAt  opcionális lejárat (a Social adhatja át)
     * @return a készlet azonosítója + a feladatprezentációk solution nélkül
     */
    public DuelSetResponse createDuelSet(UUID duelId, UUID languageId, Instant expiresAt) {
        Optional<DuelSet> existing = duelSetRepository.findByExternalDuelId(duelId);
        DuelSet duelSet = existing.orElseGet(() -> loadOrCreate(duelId, languageId, expiresAt));
        return self.buildResponse(duelSet.getId());
    }

    private DuelSet loadOrCreate(UUID duelId, UUID languageId, Instant expiresAt) {
        try {
            return self.createInTransaction(duelId, languageId, expiresAt);
        } catch (DataIntegrityViolationException e) {
            // Egyidejű létrehozás ugyanarra a duelId-re: a vesztő tranzakció
            // visszagördült. A nyertes által commitolt készletet friss
            // tranzakcióban olvassuk vissza.
            return self.readByExternalDuelId(duelId).orElseThrow(() -> e);
        }
    }

    /**
     * A készlet létrehozásának DB-munkája egyetlen tranzakcióban: a nyelv
     * jelölt feladataiból (duel-eligible) az auto-kiértékelhetőket szűri, a
     * {@code duelId}-ből determinisztikusan kiválaszt 5-öt, és beszúrja a
     * duel_set + duel_set_exercises sorokat. A flush azonnal kiváltja az
     * esetleges {@code external_duel_id} unique ütközést, amit a hívó kezel.
     *
     * @throws NotFoundException ha a nyelven nincs legalább 5 alkalmas feladat
     *     (a „mindkét fél ugyanazt az 5 feladatot kapja" invariáns nem tartható)
     */
    @Transactional
    public DuelSet createInTransaction(UUID duelId, UUID languageId, Instant expiresAt) {
        List<UUID> eligible = exerciseRepository.findDailyCandidatesByLanguageId(languageId).stream()
                // Csak az 5 auto-kiértékelhető típus (AI-típusok kizárva), a domain
                // kiválasztás Spring-mentességéhez az application rétegben szűrve.
                .filter(e -> evaluatorRegistry.isAutoEvaluable(e.getType()))
                .map(Exercise::getId)
                .toList();

        List<UUID> selected;
        try {
            selected = DuelSetSelection.select(eligible, duelId);
        } catch (IllegalStateException e) {
            throw new NotFoundException(
                    "duel_set_unavailable", "Not enough eligible exercises for a duel set");
        }

        DuelSet duelSet = new DuelSet(UUID.randomUUID(), duelId, languageId, expiresAt, Instant.now());
        duelSetRepository.save(duelSet);

        for (int position = 0; position < selected.size(); position++) {
            duelSetExerciseRepository.save(new DuelSetExercise(
                    new DuelSetExerciseId(duelSet.getId(), position), selected.get(position)));
        }
        duelSetRepository.flush();
        duelSetExerciseRepository.flush();
        return duelSet;
    }

    /**
     * A tárolt készlet friss tranzakcióban (a létrehozás versenye utáni
     * visszaolvasáshoz). Új tranzakció kell, mert a vesztő létrehozás eredeti
     * tranzakciója már visszagördült.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<DuelSet> readByExternalDuelId(UUID duelId) {
        return duelSetRepository.findByExternalDuelId(duelId);
    }

    /**
     * A készlet feladatprezentációi MEGOLDÁS NÉLKÜL, pozíció sorrendben.
     *
     * @throws NotFoundException ha a készlet nem létezik
     */
    @Transactional(readOnly = true)
    public DuelSetResponse getDuelSet(UUID setId) {
        if (!duelSetRepository.existsById(setId)) {
            throw new NotFoundException("duel_set_not_found", "Duel set not found");
        }
        return buildResponse(setId);
    }

    /**
     * A készlet azonosítójának és a solution-mentes feladatprezentációknak az
     * összeállítása pozíció sorrendben (saját read-only tranzakció). A feladatot
     * az U2 {@link ExerciseService#toPresentation} képezi le.
     */
    @Transactional(readOnly = true)
    public DuelSetResponse buildResponse(UUID setId) {
        List<DuelSetExercise> positions = duelSetExerciseRepository
                .findByIdDuelSetIdOrderByIdPositionAsc(setId);

        List<UUID> exerciseIds = positions.stream().map(DuelSetExercise::getExerciseId).toList();
        Map<UUID, Exercise> byId = exerciseRepository.findAllById(exerciseIds).stream()
                .collect(Collectors.toMap(Exercise::getId, Function.identity()));

        List<ExercisePresentationResponse> exercises = positions.stream()
                .map(DuelSetExercise::getExerciseId)
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .map(exerciseService::toPresentation)
                .toList();

        return new DuelSetResponse(setId, exercises);
    }

    /**
     * Egy résztvevő egy válaszának idempotens pontozása. Orkesztrátor: a
     * DB-munka saját tranzakcióban fut; egyidejű azonos
     * {@code (setId, userId, position)}-nál a visszagördülő tranzakció után a
     * tárolt választ egy külön olvasó tranzakcióban adja vissza (nem 500).
     *
     * @param setId      a készlet azonosítója
     * @param userId     a résztvevő azonosítója (a Socialtól, NEM JWT-ből)
     * @param position   a feladat pozíciója a mentett készletben
     * @param answerJson a nyers válasz JSON
     * @return az ADOTT válasz pontozása (nem szivárogtat más eredményt vagy
     *     megoldást)
     */
    public DuelAnswerResponse submitAnswer(UUID setId, UUID userId, int position, JsonNode answerJson) {
        try {
            return self.submitAnswerInTransaction(setId, userId, position, answerJson);
        } catch (DataIntegrityViolationException e) {
            return self.findStoredAnswer(setId, userId, position).orElseThrow(() -> e);
        }
    }

    /**
     * A párbaj-válasz pontozás DB-munkája egyetlen tranzakcióban. Unique
     * ütközésnél a {@link DataIntegrityViolationException} kibukik (a tranzakció
     * visszagördül), és a hívó a tárolt válasszal felel.
     *
     * <p><strong>Kritikus (TERV 5.5):</strong> ez a metódus SOHA nem hív
     * {@code PointAwardService}/Identity pontjóváírást — a párbaj pontja nem
     * profilpont.</p>
     */
    @Transactional
    public DuelAnswerResponse submitAnswerInTransaction(UUID setId, UUID userId, int position,
            JsonNode answerJson) {
        // (a) Idempotencia: azonos (setId, userId, position)-re a tárolt eredmény,
        // új pontozás nélkül (az eredeti score-t adja).
        DuelAnswerId answerId = new DuelAnswerId(setId, userId, position);
        Optional<DuelAnswer> existing = duelAnswerRepository.findById(answerId);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        // (b) A pozíciónak a mentett készlet eleme kell legyen (különben 404).
        DuelSetExercise setExercise = duelSetExerciseRepository
                .findByIdDuelSetIdOrderByIdPositionAsc(setId).stream()
                .filter(e -> e.getId().getPosition() == position)
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "duel_position_not_found", "Position is not part of this duel set"));

        Exercise exercise = exerciseRepository.findByIdAndEnabledTrue(setExercise.getExerciseId())
                .orElseThrow(() -> new NotFoundException(
                        "exercise_not_found", "Exercise not found or not available"));

        // (c) Kiértékelés az U3 evaluatorral (AI-típus kezelt 400 a handlerben).
        ExerciseType type = exercise.getType();
        ExerciseSolution solution = contentCodec.readSolution(type, exercise.getSolution())
                .orElseThrow(() -> new NotAutoEvaluableException(type));
        UserAnswer answer = contentCodec.readAnswer(type, answerJson);
        EvaluationResult result = evaluatorRegistry.evaluate(type, solution, answer);

        // (d) Pontozás (TERV 5.5): helyes -> max_points, egyébként 0. A párbaj
        // pontja NEM profilpont; itt NINCS Identity pontjóváírás.
        int score = result.correct() ? exercise.getMaxPoints() : 0;

        DuelAnswer duelAnswer = new DuelAnswer(answerId, answerJson, score, Instant.now());
        duelAnswerRepository.saveAndFlush(duelAnswer);

        return toResponse(duelAnswer);
    }

    /**
     * A tárolt párbaj-válasz friss tranzakcióban (az ütközés utáni
     * visszaolvasáshoz). Új tranzakció kell, mert a vesztő pontozás eredeti
     * tranzakciója már visszagördült.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<DuelAnswerResponse> findStoredAnswer(UUID setId, UUID userId, int position) {
        return duelAnswerRepository.findById(new DuelAnswerId(setId, userId, position))
                .map(DuelSetService::toResponse);
    }

    private static DuelAnswerResponse toResponse(DuelAnswer answer) {
        int score = answer.getScore() == null ? 0 : answer.getScore();
        return new DuelAnswerResponse(answer.getId().getPosition(), score, score > 0);
    }
}
