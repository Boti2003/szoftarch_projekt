package hu.bme.szoftarch.learning.api;

import java.util.List;
import java.util.UUID;

import hu.bme.szoftarch.learning.api.dto.LanguageResponse;
import hu.bme.szoftarch.learning.api.dto.LessonListItemResponse;
import hu.bme.szoftarch.learning.application.LanguageService;
import hu.bme.szoftarch.learning.application.LessonService;
import hu.bme.szoftarch.learning.infrastructure.security.CurrentUser;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nyelv- és leckelista végpontok (TERV B – U1).
 */
@RestController
@RequestMapping("/api/v1/languages")
public class LanguageController {

    private final LanguageService languageService;
    private final LessonService lessonService;
    private final CurrentUser currentUser;

    public LanguageController(LanguageService languageService, LessonService lessonService,
            CurrentUser currentUser) {
        this.languageService = languageService;
        this.lessonService = lessonService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<LanguageResponse> listLanguages() {
        return languageService.listAvailableLanguages();
    }

    @GetMapping("/{languageId}/lessons")
    public List<LessonListItemResponse> listLessons(@PathVariable UUID languageId) {
        return lessonService.listLessons(currentUser.requireUserId(), languageId);
    }
}
