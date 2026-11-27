import { fireEvent, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { expect, test } from 'vitest';
import { ScenarioChatPage } from './ScenarioChatPage';
import { LessonDetailPage } from './LessonDetailPage';
import { ExercisePlayerPage } from './ExercisePlayerPage';
import type { LessonDetailResponse } from './types';
import {
  EX_AI_SCENARIO,
  EX_TRUE_FALSE,
  LESSON_ID,
  exerciseMetaList,
  server,
} from '../../test/mswServer';

function renderScenario(exerciseId: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/scenario/${exerciseId}`]}>
        <Routes>
          <Route path="/scenario/:exerciseId" element={<ScenarioChatPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

// Renders the scenario page with lesson context plus the routes it can
// navigate to, so "back to lesson" / "next exercise" can be followed.
function renderScenarioInLesson(lessonId: string, exerciseId: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter
        initialEntries={[`/lessons/${lessonId}/scenario/${exerciseId}`]}
      >
        <Routes>
          <Route path="/lessons/:id" element={<LessonDetailPage />} />
          <Route
            path="/lessons/:lessonId/scenario/:exerciseId"
            element={<ScenarioChatPage />}
          />
          <Route
            path="/lessons/:id/exercises/:exerciseId"
            element={<ExercisePlayerPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

async function completeScenario() {
  const field = await screen.findByLabelText('Üzeneted');
  fireEvent.change(field, { target: { value: 'I would like a coffee.' } });
  fireEvent.click(screen.getByRole('button', { name: 'Küldés' }));
  await screen.findByText('Sure, anything else?');
  fireEvent.click(screen.getByRole('button', { name: 'Beszélgetés lezárása' }));
  await screen.findByText('Great conversation!');
}

test('indít -> üzenetet küld -> lezár folyamat megjeleníti a záró visszajelzést', async () => {
  renderScenario(EX_AI_SCENARIO);

  // Opening message from the start fixture.
  expect(await screen.findByText('Welcome! What can I get you?')).toBeInTheDocument();
  expect(
    screen.getByText(/Opcionális \(nem kötelező a lecke teljesítéséhez\)/),
  ).toBeInTheDocument();

  // Send one message; the AI reply fixture appears.
  const field = screen.getByLabelText('Üzeneted');
  fireEvent.change(field, { target: { value: 'I would like a coffee.' } });
  fireEvent.click(screen.getByRole('button', { name: 'Küldés' }));

  expect(await screen.findByText('Sure, anything else?')).toBeInTheDocument();

  // Complete the scenario; the closing feedback fixture renders.
  fireEvent.click(screen.getByRole('button', { name: 'Beszélgetés lezárása' }));

  expect(await screen.findByText('Great conversation!')).toBeInTheDocument();
  expect(screen.getByText('Teljesítve')).toBeInTheDocument();
  expect(screen.getByText('Szerzett pont: 27')).toBeInTheDocument();
});

test('502 ai_unavailable hiba esetén újrapróbálható üzenetet mutat', async () => {
  server.use(
    http.post('/api/v1/ai-scenarios/:exerciseId/sessions', () =>
      HttpResponse.json(
        {
          type: 'about:blank',
          title: 'Bad Gateway',
          status: 502,
          code: 'ai_unavailable',
          detail: 'AI service unavailable',
        },
        { status: 502 },
      ),
    ),
  );

  renderScenario(EX_AI_SCENARIO);

  expect(
    await screen.findByText(/Az AI szolgáltatás átmenetileg nem elérhető/),
  ).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Újra' })).toBeInTheDocument();
});

test('lecke-kontextussal a lezárás után megjelenik a "Vissza a leckéhez" és a "Következő feladat" gomb', async () => {
  // A lesson whose exercises place the AI scenario before another exercise, so
  // "next exercise" is available after completion.
  const scenarioMeta = exerciseMetaList.find(
    (e) => e.exerciseId === EX_AI_SCENARIO,
  )!;
  const trueFalseMeta = exerciseMetaList.find(
    (e) => e.exerciseId === EX_TRUE_FALSE,
  )!;
  const lessonWithTail: LessonDetailResponse = {
    lessonId: LESSON_ID,
    orderIndex: 1,
    title: 'Scenario majd feladat',
    theoryMarkdown: '# Teszt',
    exercises: [
      { ...scenarioMeta, orderIndex: 1 },
      { ...trueFalseMeta, orderIndex: 2 },
    ],
  };
  server.use(
    http.get('/api/v1/lessons/:lessonId', () =>
      HttpResponse.json(lessonWithTail),
    ),
  );

  renderScenarioInLesson(LESSON_ID, EX_AI_SCENARIO);
  await completeScenario();

  const next = screen.getByRole('button', { name: 'Következő feladat →' });
  expect(next).toBeInTheDocument();
  expect(
    screen.getByRole('link', { name: 'Vissza a leckéhez' }),
  ).toHaveAttribute('href', `/lessons/${LESSON_ID}`);

  // Following "next exercise" lands on the following exercise's player.
  fireEvent.click(next);
  expect(
    await screen.findByRole('heading', {
      name: trueFalseMeta.prompt,
    }),
  ).toBeInTheDocument();
});

test('lecke-kontextussal az utolsó feladatnál lecke-összegző hint jelenik meg', async () => {
  // A lesson whose only exercise is the scenario, so there is no next exercise.
  const scenarioMeta = exerciseMetaList.find(
    (e) => e.exerciseId === EX_AI_SCENARIO,
  )!;
  const lessonLastScenario: LessonDetailResponse = {
    lessonId: LESSON_ID,
    orderIndex: 1,
    title: 'Csak scenario',
    theoryMarkdown: '# Teszt',
    exercises: [{ ...scenarioMeta, orderIndex: 1 }],
  };
  server.use(
    http.get('/api/v1/lessons/:lessonId', () =>
      HttpResponse.json(lessonLastScenario),
    ),
  );

  renderScenarioInLesson(LESSON_ID, EX_AI_SCENARIO);
  await completeScenario();

  expect(
    screen.queryByRole('button', { name: 'Következő feladat →' }),
  ).not.toBeInTheDocument();
  expect(
    screen.getByText(/Ez volt a lecke utolsó feladata/),
  ).toBeInTheDocument();
  expect(
    screen.getByRole('link', { name: 'Vissza a leckéhez' }),
  ).toHaveAttribute('href', `/lessons/${LESSON_ID}`);
});

test('lecke-kontextus nélkül (régi route) a felső "Vissza a leckékhez" marad, nincs előrevivő gomb', async () => {
  renderScenario(EX_AI_SCENARIO);
  await completeScenario();

  expect(
    screen.getByRole('link', { name: '← Vissza a leckékhez' }),
  ).toHaveAttribute('href', '/lessons');
  expect(
    screen.queryByRole('button', { name: 'Következő feladat →' }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole('link', { name: 'Vissza a leckéhez' }),
  ).not.toBeInTheDocument();
});
