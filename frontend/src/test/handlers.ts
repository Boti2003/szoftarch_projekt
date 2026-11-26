import { http, HttpResponse } from 'msw';
import type {
  AttemptRequest,
  AttemptResponse,
  DailyChallengeTodayResponse,
  EnrollmentsResponse,
  ErrorFindingAnswer,
  ExerciseAnswer,
  ExerciseMetadataResponse,
  ExercisePresentationResponse,
  LanguageResponse,
  LessonDetailResponse,
  LessonListItemResponse,
  MatchingAnswer,
  MultipleChoiceAnswer,
  OddOneOutAnswer,
  ScenarioCompleteResponse,
  ScenarioMessageResponse,
  ScenarioStartResponse,
  TrueFalseAnswer,
} from '../features/learning/types';

// Megosztott MSW fixtures + handlers. FONTOS: ez a fájl NEM importál `msw/node`-ot
// vagy `msw/browser`-t, így biztonságosan használható böngészőben (browserMocks.ts)
// és Node-ban (mswServer.ts) is.
//
// A fixture-ök a V6+V7 seed tartalmát TÜKRÖZIK fejlesztői/demó célra (nem a valódi
// backend adata): két nyelv (angol + német), angol 9 lecke (4 kezdő + 5 B1–B2),
// német 3 lecke. A leckelista a path-beli languageId szerint ágazik el.

// --- Nyelvek ---
export const LANGUAGE_ID = '11111111-1111-1111-1111-111111111111'; // angol (visszafelé kompat.)
export const LANGUAGE_ID_EN = LANGUAGE_ID;
export const LANGUAGE_ID_DE = '11111111-1111-1111-1111-1111111100de';

export const languagesFixture: LanguageResponse[] = [
  { id: LANGUAGE_ID_EN, code: 'en', name: 'English' },
  { id: LANGUAGE_ID_DE, code: 'de', name: 'Német' },
];

export const enrollmentsFixture: EnrollmentsResponse = {
  enrollments: [
    {
      languageId: LANGUAGE_ID_EN,
      languageCode: 'en',
      languageName: 'English',
      active: true,
      startedAt: '2024-01-01T00:00:00Z',
    },
    {
      languageId: LANGUAGE_ID_DE,
      languageCode: 'de',
      languageName: 'Német',
      active: false,
      startedAt: '2024-01-01T00:00:00Z',
    },
  ],
  activeLanguageId: LANGUAGE_ID_EN,
};

// --- Angol leckék (4 kezdő + 5 B1–B2), a V6+V7 seedet tükrözve ---
export const LESSON_ID = '22222222-2222-2222-2222-222222222221'; // visszafelé kompat. (angol 1. lecke)

const englishLessons: LessonListItemResponse[] = [
  { id: LESSON_ID, orderIndex: 1, title: 'Alapok', status: 'IN_PROGRESS', unlocked: true },
  { id: '22222222-2222-2222-2222-222222222222', orderIndex: 2, title: 'Köszönések', status: 'COMPLETED', unlocked: true },
  { id: '22222222-2222-2222-2222-222222222223', orderIndex: 3, title: 'Számok és idő', status: 'NOT_STARTED', unlocked: true },
  { id: '22222222-2222-2222-2222-222222222224', orderIndex: 4, title: 'Mindennapi kifejezések', status: 'NOT_STARTED', unlocked: true },
  { id: '22222222-2222-2222-2222-222222222225', orderIndex: 5, title: 'Phrasal verbs (B1)', status: 'NOT_STARTED', unlocked: true },
  { id: '22222222-2222-2222-2222-222222222226', orderIndex: 6, title: 'Idiómák (B1–B2)', status: 'NOT_STARTED', unlocked: false },
  { id: '22222222-2222-2222-2222-222222222227', orderIndex: 7, title: 'Present perfect vs past simple (B1)', status: 'NOT_STARTED', unlocked: false },
  { id: '22222222-2222-2222-2222-222222222228', orderIndex: 8, title: 'Feltételes mondatok (B2)', status: 'NOT_STARTED', unlocked: false },
  { id: '22222222-2222-2222-2222-222222222229', orderIndex: 9, title: 'Vonzatos igék (B2)', status: 'NOT_STARTED', unlocked: false },
];

// --- Német leckék (3, kezdő→A2) ---
const germanLessons: LessonListItemResponse[] = [
  { id: '2222222a-2222-2222-2222-2222222222d1', orderIndex: 1, title: 'Begrüßung (Köszönés)', status: 'NOT_STARTED', unlocked: true },
  { id: '2222222a-2222-2222-2222-2222222222d2', orderIndex: 2, title: 'Zahlen (Számok)', status: 'NOT_STARTED', unlocked: false },
  { id: '2222222a-2222-2222-2222-2222222222d3', orderIndex: 3, title: 'Im Café (A2)', status: 'NOT_STARTED', unlocked: false },
];

const lessonsByLanguage: Record<string, LessonListItemResponse[]> = {
  [LANGUAGE_ID_EN]: englishLessons,
  [LANGUAGE_ID_DE]: germanLessons,
};

// Visszafelé kompatibilis export (a meglévő tesztek ezt használják).
export const lessonsFixture: LessonListItemResponse[] = englishLessons;

// --- Feladat-prezentációk (egy példa típusonként; demóhoz elég) ---
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
    difficulty: 'MEDIUM',
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
    maxPoints: 8,
    requiredForCompletion: true,
    duelEligible: true,
    content: { type: 'TRUE_FALSE', statement: '"Break the ice" jelentése: oldani a feszültséget.' },
  },
  [EX_MATCHING]: {
    exerciseId: EX_MATCHING,
    type: 'MATCHING',
    difficulty: 'MEDIUM',
    prompt: 'Párosítsd az idiómát a jelentésével!',
    maxPoints: 18,
    requiredForCompletion: true,
    duelEligible: true,
    content: {
      type: 'MATCHING',
      leftItems: [
        { id: 'l1', text: 'hit the books' },
        { id: 'l2', text: 'under the weather' },
      ],
      rightItems: [
        { id: 'r1', text: 'tanulni kezd' },
        { id: 'r2', text: 'gyengélkedik' },
      ],
    },
  },
  [EX_ODD_ONE_OUT]: {
    exerciseId: EX_ODD_ONE_OUT,
    type: 'ODD_ONE_OUT',
    difficulty: 'MEDIUM',
    prompt: 'Melyik nem illik a sorba?',
    maxPoints: 15,
    requiredForCompletion: true,
    duelEligible: true,
    content: {
      type: 'ODD_ONE_OUT',
      items: [
        { id: 'i1', text: 'take off' },
        { id: 'i2', text: 'take over' },
        { id: 'i3', text: 'car' },
        { id: 'i4', text: 'take up' },
      ],
    },
  },
  [EX_ERROR_FINDING]: {
    exerciseId: EX_ERROR_FINDING,
    type: 'ERROR_FINDING',
    difficulty: 'HARD',
    prompt: 'Jelöld a hibás szót!',
    maxPoints: 24,
    requiredForCompletion: true,
    duelEligible: true,
    content: {
      type: 'ERROR_FINDING',
      tokens: [
        { index: 0, text: 'If' },
        { index: 1, text: 'I' },
        { index: 2, text: 'would' },
        { index: 3, text: 'know' },
        { index: 4, text: 'the' },
        { index: 5, text: 'answer' },
      ],
    },
  },
  [EX_TRANSLATION]: {
    exerciseId: EX_TRANSLATION,
    type: 'TRANSLATION',
    difficulty: 'HARD',
    prompt: 'Fordítsd le angolra!',
    maxPoints: 30,
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
    prompt: 'Beszélgess egy állásinterjún!',
    maxPoints: 30,
    requiredForCompletion: false,
    duelEligible: false,
    content: {
      type: 'AI_SCENARIO',
      role: 'interviewer',
      situation: 'You are at a job interview for a developer position.',
      goals: ['introduce yourself', 'describe your experience', 'ask a question'],
      targetLanguageCode: 'en',
      openingMessage: 'Welcome! What can I get you?',
    },
  },
};

const exerciseMetaList: ExerciseMetadataResponse[] = Object.values(exercisePresentations).map(
  (ex, i) => ({
    exerciseId: ex.exerciseId,
    type: ex.type,
    difficulty: ex.difficulty,
    prompt: ex.prompt,
    orderIndex: i + 1,
    requiredForCompletion: ex.requiredForCompletion,
    duelEligible: ex.duelEligible,
    maxPoints: ex.maxPoints,
  }),
);

// Exportálva, hogy a navigációs tesztek az orderIndex-sorrendet ellenőrizhessék.
export { exerciseMetaList };

function lessonDetailFor(lessonId: string): LessonDetailResponse {
  const allLessons = [...englishLessons, ...germanLessons];
  const meta = allLessons.find((l) => l.id === lessonId);
  const isGerman = germanLessons.some((l) => l.id === lessonId);
  return {
    lessonId,
    orderIndex: meta?.orderIndex ?? 1,
    title: meta?.title ?? 'Lecke',
    theoryMarkdown: isGerman
      ? `# ${meta?.title ?? 'Német lecke'}\n\nEz egy **német** lecke elméleti anyaga (demó).\n\nPélda: *Guten Tag!* = Jó napot!`
      : `# ${meta?.title ?? 'Angol lecke'}\n\nEz egy **angol** lecke elméleti anyaga (demó).\n\nTöbb bekezdésnyi magyarázat, példákkal.`,
    exercises: exerciseMetaList,
  };
}

// Visszafelé kompatibilis export (a meglévő tesztek a LESSON_ID detailjét várják).
export const lessonDetailFixture: LessonDetailResponse = lessonDetailFor(LESSON_ID);

export const attemptResponseFixture: AttemptResponse = {
  attemptId: '44444444-4444-4444-4444-444444444444',
  scoreRatio: 1,
  correct: true,
  feedback: 'Szép munka!',
  pointAwardStatus: 'AWARDED',
};

// --- MOCK-ONLY helyes-megoldás térkép ---
// FIGYELEM: ez CSAK a mockban él, kizárólag a fenti demo-fixture feladatokhoz
// igazodik. NEM a valódi backend (U3 evaluator) solution-je; a valódi
// kiértékelés szerveroldalon történik. Azért kell, hogy a böngészős MSW-demó
// őszinte legyen (ne fogadjon el vakon minden beküldést 'Helyes'-ként).
// Csak az 5 AUTO típushoz adunk megoldást; a TRANSLATION és AI_SCENARIO
// AI-kiértékelésű, ezért kimarad (marad a fix siker).
type MockSolution =
  | { type: 'MULTIPLE_CHOICE'; selectedOptionIds: string[] }
  | { type: 'TRUE_FALSE'; answer: boolean }
  | { type: 'MATCHING'; pairs: { leftId: string; rightId: string }[] }
  | { type: 'ODD_ONE_OUT'; selectedItemId: string }
  | { type: 'ERROR_FINDING'; markedTokenIndexes: number[] };

export const mockSolutions: Record<string, MockSolution> = {
  // 'Hello' (id 'a') a helyes köszönés.
  [EX_MULTIPLE_CHOICE]: { type: 'MULTIPLE_CHOICE', selectedOptionIds: ['a'] },
  // "Break the ice" = oldani a feszültséget → igaz.
  [EX_TRUE_FALSE]: { type: 'TRUE_FALSE', answer: true },
  // hit the books → tanulni kezd (l1-r1); under the weather → gyengélkedik (l2-r2).
  [EX_MATCHING]: {
    type: 'MATCHING',
    pairs: [
      { leftId: 'l1', rightId: 'r1' },
      { leftId: 'l2', rightId: 'r2' },
    ],
  },
  // 'car' (id 'i3') a kilógó elem a phrasal verb-ök között.
  [EX_ODD_ONE_OUT]: { type: 'ODD_ONE_OUT', selectedItemId: 'i3' },
  // "If I would know the answer" – a hibás szó a "would" (index 2).
  [EX_ERROR_FINDING]: { type: 'ERROR_FINDING', markedTokenIndexes: [2] },
};

function sameSet<T>(a: T[], b: T[]): boolean {
  if (a.length !== b.length) {
    return false;
  }
  const bs = new Set(b);
  return a.every((x) => bs.has(x));
}

function isAnswerCorrect(solution: MockSolution, answer: ExerciseAnswer): boolean {
  switch (solution.type) {
    case 'MULTIPLE_CHOICE':
      return sameSet(
        solution.selectedOptionIds,
        (answer as MultipleChoiceAnswer).selectedOptionIds ?? [],
      );
    case 'TRUE_FALSE':
      return (answer as TrueFalseAnswer).answer === solution.answer;
    case 'MATCHING': {
      const expected = solution.pairs.map((p) => `${p.leftId}:${p.rightId}`);
      const given = ((answer as MatchingAnswer).pairs ?? []).map(
        (p) => `${p.leftId}:${p.rightId}`,
      );
      return sameSet(expected, given);
    }
    case 'ODD_ONE_OUT':
      return (answer as OddOneOutAnswer).selectedItemId === solution.selectedItemId;
    case 'ERROR_FINDING':
      return sameSet(
        solution.markedTokenIndexes,
        (answer as ErrorFindingAnswer).markedTokenIndexes ?? [],
      );
    default:
      return false;
  }
}

// A beküldött választ a mockSolutions szerint értékeli ki. Ismeretlen
// exerciseId / hiányzó mockSolution esetén a jelenlegi fix sikert adja vissza
// (TRANSLATION, AI_SCENARIO és minden nem térképezett feladat így marad).
function evaluateAttempt(exerciseId: string, body: AttemptRequest): AttemptResponse {
  const solution = mockSolutions[exerciseId];
  if (!solution) {
    return attemptResponseFixture;
  }
  const correct = isAnswerCorrect(solution, body.answer);
  if (correct) {
    return {
      attemptId: attemptResponseFixture.attemptId,
      scoreRatio: 1,
      correct: true,
      feedback: 'Szép munka!',
      pointAwardStatus: 'AWARDED',
    };
  }
  return {
    attemptId: attemptResponseFixture.attemptId,
    scoreRatio: 0,
    correct: false,
    feedback: 'Nem egészen, próbáld újra!',
    pointAwardStatus: 'NOT_APPLICABLE',
  };
}

export const dailyChallengeFixture: DailyChallengeTodayResponse = {
  challengeId: '55555555-5555-5555-5555-555555555555',
  date: '2024-01-02',
  languageId: LANGUAGE_ID_EN,
  exercises: [
    exercisePresentations[EX_MULTIPLE_CHOICE],
    exercisePresentations[EX_TRUE_FALSE],
    exercisePresentations[EX_MATCHING],
  ],
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

// Alapértelmezett handlerek. A gazdák feature-önként bővítik.
// --- Identity (A) auth-mockok (integrációs merge: a korábbi mswServer.ts-ből
// áthozva ide, hogy a node/browser szétválasztás megmaradjon) ---
export const mockUser = {
  id: '11111111-1111-1111-1111-111111111111',
  email: 'anna@example.com',
  username: 'annakovacs',
  displayName: 'Anna Kovacs',
  totalPoints: 150,
  level: 2,
  nextLevelPoints: 200,
  createdAt: '2026-11-20T10:00:00Z',
};

const authResponse = {
  accessToken: 'mock-access-token',
  tokenType: 'Bearer',
  expiresInSeconds: 300,
  user: {
    id: mockUser.id,
    email: mockUser.email,
    username: mockUser.username,
    displayName: mockUser.displayName,
  },
};

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

  // --- Identity (A) auth / profil / leaderboard handlerek ---
  http.post('/api/v1/auth/refresh', () => HttpResponse.json(authResponse)),
  http.post('/api/v1/auth/login', () => HttpResponse.json(authResponse)),
  http.post('/api/v1/auth/register', () => HttpResponse.json(mockUser, { status: 201 })),
  http.post('/api/v1/auth/logout', () => new HttpResponse(null, { status: 204 })),
  http.post('/api/v1/auth/change-password', async ({ request }) => {
    const body = (await request.json()) as { currentPassword?: string; newPassword?: string };
    if (body.currentPassword === 'wrongpassword') {
      return HttpResponse.json(
        { title: 'Unauthorized', status: 401, detail: 'Current password is incorrect' },
        { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
      );
    }
    return HttpResponse.json({ ...authResponse, accessToken: 'new-mock-access-token' });
  }),
  http.get('/api/v1/users/me', () => HttpResponse.json(mockUser)),
  http.patch('/api/v1/users/me', async ({ request }) => {
    const body = (await request.json()) as { displayName?: string };
    return HttpResponse.json({
      ...mockUser,
      displayName: body.displayName || mockUser.displayName,
    });
  }),
  http.get('/api/v1/leaderboard', () =>
    HttpResponse.json({
      entries: [
        {
          rank: 1,
          userId: '22222222-2222-2222-2222-222222222222',
          username: 'bela',
          displayName: 'Bela Kovacs',
          level: 4,
          totalPoints: 500,
        },
        {
          rank: 2,
          userId: mockUser.id,
          username: mockUser.username,
          displayName: mockUser.displayName,
          level: mockUser.level,
          totalPoints: mockUser.totalPoints,
        },
      ],
      myRank: {
        rank: 2,
        userId: mockUser.id,
        username: mockUser.username,
        displayName: mockUser.displayName,
        level: mockUser.level,
        totalPoints: mockUser.totalPoints,
      },
      page: 0,
      size: 10,
      totalElements: 2,
      totalPages: 1,
    }),
  ),

  // --- Learning / daily / scenario handlers ---
  http.get('/api/v1/languages', () => HttpResponse.json(languagesFixture)),

  http.get('/api/v1/learning/enrollments', () => HttpResponse.json(enrollmentsFixture)),

  http.put('/api/v1/learning/enrollments/:languageId/active', () =>
    new HttpResponse(null, { status: 204 }),
  ),

  // A leckelista a path-beli languageId szerint ágazik el (en vs de).
  http.get('/api/v1/languages/:languageId/lessons', ({ params }) => {
    const lessons = lessonsByLanguage[params.languageId as string] ?? [];
    return HttpResponse.json(lessons);
  }),

  http.get('/api/v1/lessons/:lessonId', ({ params }) =>
    HttpResponse.json(lessonDetailFor(params.lessonId as string)),
  ),

  http.get('/api/v1/exercises/:exerciseId', ({ params }) => {
    const presentation = exercisePresentations[params.exerciseId as string];
    if (!presentation) {
      return new HttpResponse(null, { status: 404 });
    }
    return HttpResponse.json(presentation);
  }),

  http.post('/api/v1/exercises/:exerciseId/attempts', async ({ params, request }) => {
    const body = (await request.json()) as AttemptRequest;
    return HttpResponse.json(evaluateAttempt(params.exerciseId as string, body));
  }),

  http.get('/api/v1/daily-challenges/today', () =>
    HttpResponse.json(dailyChallengeFixture),
  ),

  http.post(
    '/api/v1/daily-challenges/today/exercises/:exerciseId/attempts',
    async ({ params, request }) => {
      const body = (await request.json()) as AttemptRequest;
      return HttpResponse.json(evaluateAttempt(params.exerciseId as string, body));
    },
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
