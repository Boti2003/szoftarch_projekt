// TanStack Query hooks for the daily-challenge feature (see the learning
// feature hooks for the useQuery/useMutation pattern).
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  getDailyToday,
  newIdempotencyKey,
  submitDailyAttempt,
} from '../learning/api';
import type {
  AttemptResponse,
  DailyChallengeTodayResponse,
  ExerciseAnswer,
  Uuid,
} from '../learning/types';

export function useDailyToday(languageId: Uuid | null | undefined) {
  return useQuery<DailyChallengeTodayResponse>({
    queryKey: ['daily', 'today', languageId],
    queryFn: () => getDailyToday(languageId as Uuid),
    enabled: Boolean(languageId),
  });
}

interface SubmitDailyVars {
  exerciseId: Uuid;
  answer: ExerciseAnswer;
}

export function useSubmitDailyAttempt(languageId: Uuid | null | undefined) {
  return useMutation<AttemptResponse, Error, SubmitDailyVars>({
    mutationFn: ({ exerciseId, answer }: SubmitDailyVars) =>
      submitDailyAttempt(
        exerciseId,
        languageId as Uuid,
        answer,
        newIdempotencyKey(),
      ),
  });
}
