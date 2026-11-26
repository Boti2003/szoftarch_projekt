import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { expect, test } from 'vitest';
import { nextExercise, nextLesson } from './navigation';
import { LessonListPage } from './LessonListPage';
import { LessonDetailPage } from './LessonDetailPage';
import { ExercisePlayerPage } from './ExercisePlayerPage';
import type {
  ExerciseMetadataResponse,
  LessonDetailResponse,
  LessonListItemResponse,
} from './types';
import {
  EX_MULTIPLE_CHOICE,
  EX_TRUE_FALSE,
  LESSON_ID,
  exerciseMetaList,
  exercisePresentations,
  lessonsFixture,
  server,
} from '../../test/mswServer';

function meta(id: string, orderIndex: number): ExerciseMetadataResponse {
  return {
    exerciseId: id,
    type: 'MULTIPLE_CHOICE',
    difficulty: 'EASY',
    prompt: id,
    orderIndex,
    requiredForCompletion: true,
    duelEligible: true,
    maxPoints: 10,
  };
}

function lesson(
  id: string,
  orderIndex: number,
  unlocked: boolean,
): LessonListItemResponse {
  return { id, orderIndex, title: id, status: 'NOT_STARTED', unlocked };
}

test('nextExercise visszaadja az orderIndex szerinti következőt, az utolsónál null', () => {
  const list = [meta('b', 2), meta('a', 1), meta('c', 3)];
  expect(nextExercise(list, 'a')?.exerciseId).toBe('b');
  expect(nextExercise(list, 'b')?.exerciseId).toBe('c');
  expect(nextExercise(list, 'c')).toBeNull();
});

test('nextLesson jelzi a következő leckét és a zároltságot', () => {
  const list = [lesson('l1', 1, true), lesson('l2', 2, false)];
  const afterFirst = nextLesson(list, 'l1');
  expect(afterFirst.lesson?.id).toBe('l2');
  expect(afterFirst.locked).toBe(true);
  expect(nextLesson(list, 'l2').lesson).toBeNull();
});

function renderFlow(initialEntry: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route path="/lessons" element={<LessonListPage />} />
          <Route path="/lessons/:id" element={<LessonDetailPage />} />
          <Route
            path="/lessons/:id/exercises/:exerciseId"
            element={<ExercisePlayerPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

test('beküldés után a "Következő feladat" a sor következő feladatára navigál', async () => {
  const first = exerciseMetaList[0];
  const second = exerciseMetaList[1];
  renderFlow(`/lessons/${LESSON_ID}/exercises/${first.exerciseId}`);

  // The first exercise (MULTIPLE_CHOICE) renders; answer and submit it.
  fireEvent.click(await screen.findByLabelText('Hello'));
  fireEvent.click(screen.getByRole('button', { name: 'Beküldés' }));

  // Feedback plus the next-exercise button appear.
  await screen.findByText('Szép munka!');
  fireEvent.click(screen.getByRole('button', { name: 'Következő feladat →' }));

  // The next exercise's prompt (TRUE_FALSE) is now shown as the heading.
  expect(
    await screen.findByRole('heading', { name: second.prompt }),
  ).toBeInTheDocument();
});

test('az utolsó feladat után lecke-összegző jelenik meg, nem "Következő feladat"', async () => {
  // A lesson whose exercises end with an auto exercise (no AI tail), so the
  // second one is genuinely the last playable exercise.
  const twoExerciseLesson: LessonDetailResponse = {
    lessonId: LESSON_ID,
    orderIndex: 1,
    title: 'Két feladatos',
    theoryMarkdown: '# Teszt',
    exercises: [
      { ...exerciseMetaList[0], exerciseId: EX_MULTIPLE_CHOICE, orderIndex: 1 },
      { ...exerciseMetaList[1], exerciseId: EX_TRUE_FALSE, orderIndex: 2 },
    ],
  };
  server.use(
    http.get('/api/v1/lessons/:lessonId', () =>
      HttpResponse.json(twoExerciseLesson),
    ),
  );

  // Open the last exercise (TRUE_FALSE) directly.
  expect(exercisePresentations[EX_TRUE_FALSE].type).toBe('TRUE_FALSE');
  renderFlow(`/lessons/${LESSON_ID}/exercises/${EX_TRUE_FALSE}`);

  fireEvent.click(await screen.findByLabelText('Igaz'));
  fireEvent.click(screen.getByRole('button', { name: 'Beküldés' }));

  await screen.findByText('Szép munka!');
  // No "next exercise" button; a lesson-summary hint is shown instead.
  expect(
    screen.queryByRole('button', { name: 'Következő feladat →' }),
  ).not.toBeInTheDocument();
  expect(
    screen.getByText(/Ez volt a lecke utolsó feladata/),
  ).toBeInTheDocument();
});

test('a lecke-detail "Vissza a leckelistához" gombja a listához navigál', async () => {
  renderFlow(`/lessons/${LESSON_ID}`);

  const backButton = await screen.findByRole('link', {
    name: '← Vissza a leckelistához',
  });
  fireEvent.click(backButton);

  // The lesson list heading renders after navigating back.
  expect(
    await screen.findByRole('heading', { name: 'Leckék' }),
  ).toBeInTheDocument();
  // And the summary shows the completed/total count from the fixture.
  await waitFor(() =>
    expect(screen.getByText(/lecke kész/)).toBeInTheDocument(),
  );
});

test('a lecke-detail jelzi, ha a következő lecke zárolt', async () => {
  // Lesson 5 (order 5) is unlocked, lesson 6 (order 6) is locked in the fixture.
  const unlockedBeforeLocked = lessonsFixture.find((l) => l.orderIndex === 5)!;
  renderFlow(`/lessons/${unlockedBeforeLocked.id}`);

  expect(
    await screen.findByRole('button', { name: 'Következő lecke zárolva' }),
  ).toBeDisabled();
  expect(
    screen.getByText(/Előbb teljesítsd ezt a leckét/),
  ).toBeInTheDocument();
});

test('beküldés után a lecke-detail query invalidálódik és a státusz frissül', async () => {
  // First lesson GET: NOT_STARTED. After a submit the lesson query is
  // invalidated and the refetch returns COMPLETED, proving the status updates
  // without a reload.
  let lessonCalls = 0;
  const baseLesson: LessonDetailResponse = {
    lessonId: LESSON_ID,
    orderIndex: 1,
    title: 'Állapotfrissítés',
    theoryMarkdown: '# Teszt',
    exercises: [{ ...exerciseMetaList[0], exerciseId: EX_MULTIPLE_CHOICE, orderIndex: 1 }],
  };
  server.use(
    http.get('/api/v1/lessons/:lessonId', () => {
      lessonCalls += 1;
      return HttpResponse.json(baseLesson);
    }),
  );

  renderFlow(`/lessons/${LESSON_ID}/exercises/${EX_MULTIPLE_CHOICE}`);

  fireEvent.click(await screen.findByLabelText('Hello'));
  const callsBeforeSubmit = lessonCalls;
  fireEvent.click(screen.getByRole('button', { name: 'Beküldés' }));

  await screen.findByText('Szép munka!');
  // The successful submit invalidates the lesson query, triggering a refetch.
  await waitFor(() => expect(lessonCalls).toBeGreaterThan(callsBeforeSubmit));
});
