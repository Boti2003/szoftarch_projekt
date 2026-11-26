// Pure navigation helpers for the learning flow. They derive the "next"
// targets from data the backend already returns (lesson.exercises ordered by
// orderIndex, and the lesson list with unlocked/orderIndex), so no backend
// change is needed. AI_SCENARIO exercises live on a different route than the
// auto/translation player, so the route is computed from the exercise type.
import type {
  ExerciseMetadataResponse,
  ExerciseType,
  LessonListItemResponse,
} from './types';

// AI scenario exercises are played on /scenario/:exerciseId; everything else
// (auto types + translation) uses the in-lesson exercise player route.
export function exerciseRoute(
  lessonId: string,
  exerciseId: string,
  type: ExerciseType,
): string {
  return type === 'AI_SCENARIO'
    ? `/scenario/${exerciseId}`
    : `/lessons/${lessonId}/exercises/${exerciseId}`;
}

// Returns the exercise that follows the given one in orderIndex order, or null
// if the given exercise is the last (or unknown).
export function nextExercise(
  exercises: ExerciseMetadataResponse[],
  currentExerciseId: string,
): ExerciseMetadataResponse | null {
  const ordered = [...exercises].sort((a, b) => a.orderIndex - b.orderIndex);
  const index = ordered.findIndex((e) => e.exerciseId === currentExerciseId);
  if (index === -1 || index === ordered.length - 1) return null;
  return ordered[index + 1];
}

export interface NextLessonResult {
  // The next lesson after the current one in orderIndex order, if any.
  lesson: LessonListItemResponse | null;
  // True when that next lesson exists but is still locked.
  locked: boolean;
}

// Returns the lesson that follows the current one (by orderIndex) and whether
// it is still locked. A locked next lesson is surfaced as a disabled action
// with an explanation rather than hidden.
export function nextLesson(
  lessons: LessonListItemResponse[],
  currentLessonId: string,
): NextLessonResult {
  const ordered = [...lessons].sort((a, b) => a.orderIndex - b.orderIndex);
  const index = ordered.findIndex((l) => l.id === currentLessonId);
  if (index === -1 || index === ordered.length - 1) {
    return { lesson: null, locked: false };
  }
  const candidate = ordered[index + 1];
  return { lesson: candidate, locked: !candidate.unlocked };
}
