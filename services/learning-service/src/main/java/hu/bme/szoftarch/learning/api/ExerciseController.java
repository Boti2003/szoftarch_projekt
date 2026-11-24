package hu.bme.szoftarch.learning.api;

import java.util.UUID;

import hu.bme.szoftarch.learning.api.dto.ExercisePresentationResponse;
import hu.bme.szoftarch.learning.application.ExerciseService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feladat-prezentáció végpont (TERV B – U2, 7.4): egy feladat teljes
 * prezentációja a payload alapján, a helyes megoldás nélkül (TERV 5.2).
 */
@RestController
@RequestMapping("/api/v1/exercises")
public class ExerciseController {

    private final ExerciseService exerciseService;

    public ExerciseController(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    @GetMapping("/{exerciseId}")
    public ExercisePresentationResponse getExercise(@PathVariable UUID exerciseId) {
        return exerciseService.getExercisePresentation(exerciseId);
    }
}
