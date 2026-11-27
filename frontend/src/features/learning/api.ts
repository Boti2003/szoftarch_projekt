// One function per learning-service endpoint (see plan.md "API facts").
import { apiGet, apiSend } from '../../api/client';
import type {
  AttemptResponse,
  DailyChallengeTodayResponse,
  EnrollmentsResponse,
  ExerciseAnswer,
  ExercisePresentationResponse,
  LanguageResponse,
  LessonDetailResponse,
  LessonListItemResponse,
  ScenarioCompleteResponse,
  ScenarioMessageResponse,
  ScenarioStartResponse,
  Uuid,
} from './types';

// Idempotency-Key for every mutating attempt/scenario call.
export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

export function listLanguages(): Promise<LanguageResponse[]> {
  return apiGet<LanguageResponse[]>('/languages');
}

export function getEnrollments(): Promise<EnrollmentsResponse> {
  return apiGet<EnrollmentsResponse>('/learning/enrollments');
}

export function activateLanguage(languageId: Uuid): Promise<void> {
  return apiSend<void>('PUT', `/learning/enrollments/${languageId}/active`);
}

export function listLessons(languageId: Uuid): Promise<LessonListItemResponse[]> {
  return apiGet<LessonListItemResponse[]>(`/languages/${languageId}/lessons`);
}

export function getLesson(lessonId: Uuid): Promise<LessonDetailResponse> {
  return apiGet<LessonDetailResponse>(`/lessons/${lessonId}`);
}

export function getExercise(exerciseId: Uuid): Promise<ExercisePresentationResponse> {
  return apiGet<ExercisePresentationResponse>(`/exercises/${exerciseId}`);
}

export function submitAttempt(
  exerciseId: Uuid,
  answer: ExerciseAnswer,
  idempotencyKey: string,
): Promise<AttemptResponse> {
  return apiSend<AttemptResponse>('POST', `/exercises/${exerciseId}/attempts`, {
    body: { answer },
    idempotencyKey,
  });
}

export function getDailyToday(languageId: Uuid): Promise<DailyChallengeTodayResponse> {
  return apiGet<DailyChallengeTodayResponse>(
    `/daily-challenges/today?languageId=${languageId}`,
  );
}

export function submitDailyAttempt(
  exerciseId: Uuid,
  languageId: Uuid,
  answer: ExerciseAnswer,
  idempotencyKey: string,
): Promise<AttemptResponse> {
  return apiSend<AttemptResponse>(
    'POST',
    `/daily-challenges/today/exercises/${exerciseId}/attempts?languageId=${languageId}`,
    { body: { answer }, idempotencyKey },
  );
}

export function startScenario(
  exerciseId: Uuid,
  idempotencyKey: string,
): Promise<ScenarioStartResponse> {
  return apiSend<ScenarioStartResponse>(
    'POST',
    `/ai-scenarios/${exerciseId}/sessions`,
    { idempotencyKey },
  );
}

export function sendScenarioMessage(
  sessionId: Uuid,
  message: string,
): Promise<ScenarioMessageResponse> {
  return apiSend<ScenarioMessageResponse>(
    'POST',
    `/ai-scenarios/sessions/${sessionId}/messages`,
    { body: { message } },
  );
}

export function completeScenario(
  sessionId: Uuid,
  idempotencyKey: string,
): Promise<ScenarioCompleteResponse> {
  return apiSend<ScenarioCompleteResponse>(
    'POST',
    `/ai-scenarios/sessions/${sessionId}/complete`,
    { idempotencyKey },
  );
}
