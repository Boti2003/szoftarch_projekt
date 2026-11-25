import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';
import type {
  AttemptResponse,
  DailyChallengeTodayResponse,
  EnrollmentsResponse,
  ExercisePresentationResponse,
  LanguageResponse,
  LessonDetailResponse,
  LessonListItemResponse,
  ScenarioCompleteResponse,
  ScenarioMessageResponse,
  ScenarioStartResponse,
} from '../features/learning/types';

// --- Reusable learning fixtures (feature tests can override via server.use(...)) ---
export const LANGUAGE_ID = '11111111-1111-1111-1111-111111111111';

export const languagesFixture: LanguageResponse[] = [
  { id: LANGUAGE_ID, code: 'en', name: 'English' },
];

export const enrollmentsFixture: EnrollmentsResponse = {
  enrollments: [
    {
      languageId: LANGUAGE_ID,
      languageCode: 'en',
      languageName: 'English',
      active: true,
      startedAt: '2024-01-01T00:00:00Z',
    },
  ],
  activeLanguageId: LANGUAGE_ID,
};

export const LESSON_ID = '22222222-2222-2222-2222-222222222221';

export const lessonsFixture: LessonListItemResponse[] = [
  { id: LESSON_ID, orderIndex: 1, title: 'Alapok', status: 'IN_PROGRESS', unlocked: true },
  {
    id: '22222222-2222-2222-2222-222222222222',
    orderIndex: 2,
    title: 'Köszönések',
    status: 'COMPLETED',
    unlocked: true,
  },
  {
    id: '22222222-2222-2222-2222-222222222223',
    orderIndex: 3,
    title: 'Haladó',
    status: 'NOT_STARTED',
    unlocked: false,
  },
];

// One exercise per auto type plus TRANSLATION and AI_SCENARIO.
export const EX_MULTIPLE_CHOICE = '33333333-3333-3333-3333-333333333331';
export const EX_TRUE_FALSE = '33333333-3333-3333-3333-333333333332';
export const EX_MATCHING = '33333333-3333-3333-3333-333333333333';
export const EX_ODD_ONE_OUT = '33333333-3333-3333-3333-333333333334';
export const EX_ERROR_FINDING = '33333333-3333-3333-3333-333333333335';
export const EX_TRANSLATION = '33333333-3333-3333-3333-333333333336';
export const EX_AI_SCENARIO = '33333333-3333-3333-3333-333333333337';

export const exercisePresentations: Record<string, ExercisePresentationResponse> = {
  [EX_MULTIPLE_CHOICE]: {
    exerciseId: EX_MULTIPLE_CHOICE,
    type: 'MULTIPLE_CHOICE',
    difficulty: 'EASY',
    prompt: 'Melyik a helyes?',
    maxPoints: 10,
    requiredForCompletion: true,
    duelEligible: true,
    content: {
      type: 'MULTIPLE_CHOICE',
      multiSelect: false,
      options: [
        { id: 'a', text: 'Hello' },
        { id: 'b', text: 'Hola' },
      ],
    },
  },
  [EX_TRUE_FALSE]: {
    exerciseId: EX_TRUE_FALSE,
    type: 'TRUE_FALSE',
    difficulty: 'EASY',
    prompt: 'Igaz vagy hamis?',
    maxPoints: 10,
    requiredForCompletion: true,
    duelEligible: true,
    content: { type: 'TRUE_FALSE', statement: '"Dog" magyarul kutya.' },
  },
  [EX_MATCHING]: {
    exerciseId: EX_MATCHING,
    type: 'MATCHING',
    difficulty: 'MEDIUM',
    prompt: 'Párosítsd!',
    maxPoints: 10,
    requiredForCompletion: true,
    duelEligible: true,
    content: {
      type: 'MATCHING',
      leftItems: [
        { id: 'l1', text: 'dog' },
        { id: 'l2', text: 'cat' },
      ],
      rightItems: [
        { id: 'r1', text: 'kutya' },
        { id: 'r2', text: 'macska' },
      ],
    },
  },
  [EX_ODD_ONE_OUT]: {
    exerciseId: EX_ODD_ONE_OUT,
    type: 'ODD_ONE_OUT',
    difficulty: 'MEDIUM',
    prompt: 'Melyik nem illik a sorba?',
    maxPoints: 10,
    requiredForCompletion: true,
    duelEligible: true,
    content: {
      type: 'ODD_ONE_OUT',
      items: [
        { id: 'i1', text: 'apple' },
        { id: 'i2', text: 'banana' },
        { id: 'i3', text: 'car' },
      ],
    },
  },
  [EX_ERROR_FINDING]: {
    exerciseId: EX_ERROR_FINDING,
    type: 'ERROR_FINDING',
    difficulty: 'HARD',
    prompt: 'Jelöld a hibát!',
    maxPoints: 10,
    requiredForCompletion: true,
    duelEligible: true,
    content: {
      type: 'ERROR_FINDING',
      tokens: [
        { index: 0, text: 'She' },
        { index: 1, text: 'go' },
        { index: 2, text: 'home' },
      ],
    },
  },
  [EX_TRANSLATION]: {
    exerciseId: EX_TRANSLATION,
    type: 'TRANSLATION',
    difficulty: 'HARD',
    prompt: 'Fordítsd le!',
    maxPoints: 20,
    requiredForCompletion: false,
    duelEligible: false,
    content: {
      type: 'TRANSLATION',
      sourceText: 'Jó reggelt!',
      targetLanguageCode: 'en',
      rubric: ['greeting', 'morning'],
    },
  },
  [EX_AI_SCENARIO]: {
    exerciseId: EX_AI_SCENARIO,
    type: 'AI_SCENARIO',
    difficulty: 'HARD',
    prompt: 'Beszélgess!',
    maxPoints: 30,
    requiredForCompletion: false,
    duelEligible: false,
    content: {
      type: 'AI_SCENARIO',
      role: 'waiter',
      situation: 'You are at a restaurant.',
      goals: ['order food', 'ask for the bill'],
      targetLanguageCode: 'en',
      openingMessage: 'Welcome! What can I get you?',
    },
  },
};

export const lessonDetailFixture: LessonDetailResponse = {
  lessonId: LESSON_ID,
  orderIndex: 1,
  title: 'Alapok',
  theoryMarkdown: '# Alapok\n\nEz egy **rövid** elméleti anyag.',
  exercises: Object.values(exercisePresentations).map((ex) => ({
    exerciseId: ex.exerciseId,
    type: ex.type,
    difficulty: ex.difficulty,
    prompt: ex.prompt,
    orderIndex: 0,
    requiredForCompletion: ex.requiredForCompletion,
    duelEligible: ex.duelEligible,
    maxPoints: ex.maxPoints,
  })).map((meta, i) => ({ ...meta, orderIndex: i + 1 })),
};

export const attemptResponseFixture: AttemptResponse = {
  attemptId: '44444444-4444-4444-4444-444444444444',
  scoreRatio: 1,
  correct: true,
  feedback: 'Szép munka!',
  pointAwardStatus: 'AWARDED',
};

export const dailyChallengeFixture: DailyChallengeTodayResponse = {
  challengeId: '55555555-5555-5555-5555-555555555555',
  date: '2024-01-02',
  languageId: LANGUAGE_ID,
  exercises: [exercisePresentations[EX_MULTIPLE_CHOICE], exercisePresentations[EX_TRUE_FALSE]],
  ownStatus: { status: 'IN_PROGRESS', correctCount: 0, bonusAwarded: false },
};

export const scenarioStartFixture: ScenarioStartResponse = {
  sessionId: '66666666-6666-6666-6666-666666666666',
  attemptId: '77777777-7777-7777-7777-777777777777',
  openingMessage: 'Welcome! What can I get you?',
};

export const scenarioMessageFixture: ScenarioMessageResponse = {
  aiMessage: 'Sure, anything else?',
  turnCount: 1,
};

export const scenarioCompleteFixture: ScenarioCompleteResponse = {
  scoreRatio: 0.9,
  completed: true,
  points: 27,
  feedback: 'Great conversation!',
};

// Alapértelmezett handlerek a skeletonhoz. A gazdák feature-önként bővítik.
export const handlers = [
  http.get('/api/v1/system/health', () =>
    HttpResponse.json({
      status: 'UP',
      gateway: 'UP',
      identity: 'UP',
      learning: 'UP',
      social: 'UP',
      ai: 'UP',
    }),
  ),

  // --- Learning / daily / scenario handlers (additive) ---
  http.get('/api/v1/languages', () => HttpResponse.json(languagesFixture)),

  http.get('/api/v1/learning/enrollments', () => HttpResponse.json(enrollmentsFixture)),

  http.put('/api/v1/learning/enrollments/:languageId/active', () =>
    new HttpResponse(null, { status: 204 }),
  ),

  http.get('/api/v1/languages/:languageId/lessons', () =>
    HttpResponse.json(lessonsFixture),
  ),

  http.get('/api/v1/lessons/:lessonId', () => HttpResponse.json(lessonDetailFixture)),

  http.get('/api/v1/exercises/:exerciseId', ({ params }) => {
    const presentation = exercisePresentations[params.exerciseId as string];
    if (!presentation) {
      return new HttpResponse(null, { status: 404 });
    }
    return HttpResponse.json(presentation);
  }),

  http.post('/api/v1/exercises/:exerciseId/attempts', () =>
    HttpResponse.json(attemptResponseFixture),
  ),

  http.get('/api/v1/daily-challenges/today', () =>
    HttpResponse.json(dailyChallengeFixture),
  ),

  http.post('/api/v1/daily-challenges/today/exercises/:exerciseId/attempts', () =>
    HttpResponse.json(attemptResponseFixture),
  ),

  http.post('/api/v1/ai-scenarios/:exerciseId/sessions', () =>
    HttpResponse.json(scenarioStartFixture),
  ),

  http.post('/api/v1/ai-scenarios/sessions/:sessionId/messages', () =>
    HttpResponse.json(scenarioMessageFixture),
  ),

  http.post('/api/v1/ai-scenarios/sessions/:sessionId/complete', () =>
    HttpResponse.json(scenarioCompleteFixture),
  ),
];

export const server = setupServer(...handlers);
