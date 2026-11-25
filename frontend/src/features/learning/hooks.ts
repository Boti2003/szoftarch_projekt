// TanStack Query hooks for the learning feature (see HealthDashboard for the
// useQuery pattern). Query keys are stable arrays so mutations can invalidate.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  activateLanguage,
  completeScenario,
  getEnrollments,
  getExercise,
  getLesson,
  listLanguages,
  listLessons,
  newIdempotencyKey,
  sendScenarioMessage,
  startScenario,
  submitAttempt,
} from './api';
import type {
  AttemptResponse,
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

export const enrollmentsQueryKey = ['learning', 'enrollments'] as const;

export function useLanguages() {
  return useQuery<LanguageResponse[]>({
    queryKey: ['learning', 'languages'],
    queryFn: listLanguages,
  });
}

export function useEnrollments() {
  return useQuery<EnrollmentsResponse>({
    queryKey: enrollmentsQueryKey,
    queryFn: getEnrollments,
  });
}

export function useActivateLanguage() {
  const queryClient = useQueryClient();
  return useMutation<void, Error, Uuid>({
    mutationFn: (languageId: Uuid) => activateLanguage(languageId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: enrollmentsQueryKey });
    },
  });
}

export function useLessons(languageId: Uuid | null | undefined) {
  return useQuery<LessonListItemResponse[]>({
    queryKey: ['learning', 'lessons', languageId],
    queryFn: () => listLessons(languageId as Uuid),
    enabled: Boolean(languageId),
  });
}

export function useLesson(lessonId: Uuid | undefined) {
  return useQuery<LessonDetailResponse>({
    queryKey: ['learning', 'lesson', lessonId],
    queryFn: () => getLesson(lessonId as Uuid),
    enabled: Boolean(lessonId),
  });
}

export function useExercise(exerciseId: Uuid | undefined) {
  return useQuery<ExercisePresentationResponse>({
    queryKey: ['learning', 'exercise', exerciseId],
    queryFn: () => getExercise(exerciseId as Uuid),
    enabled: Boolean(exerciseId),
  });
}

export function useSubmitAttempt(exerciseId: Uuid) {
  return useMutation<AttemptResponse, Error, ExerciseAnswer>({
    mutationFn: (answer: ExerciseAnswer) =>
      submitAttempt(exerciseId, answer, newIdempotencyKey()),
  });
}

export function useStartScenario(exerciseId: Uuid) {
  return useMutation<ScenarioStartResponse, Error, void>({
    mutationFn: () => startScenario(exerciseId, newIdempotencyKey()),
  });
}

export function useSendScenarioMessage(sessionId: Uuid | undefined) {
  return useMutation<ScenarioMessageResponse, Error, string>({
    mutationFn: (message: string) =>
      sendScenarioMessage(sessionId as Uuid, message),
  });
}

export function useCompleteScenario(sessionId: Uuid | undefined) {
  return useMutation<ScenarioCompleteResponse, Error, void>({
    mutationFn: () => completeScenario(sessionId as Uuid, newIdempotencyKey()),
  });
}
