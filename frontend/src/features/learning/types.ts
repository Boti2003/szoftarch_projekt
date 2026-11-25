// TypeScript types mirroring the learning-service DTOs (see plan.md "API facts").
// ExerciseContent/answer payloads are serialized WITHOUT a type-discriminator
// wrapper, so the discriminated unions below key on the top-level `type` field.

export type Uuid = string;

export interface LanguageResponse {
  id: Uuid;
  code: string;
  name: string;
}

export interface EnrollmentResponse {
  languageId: Uuid;
  languageCode: string;
  languageName: string;
  active: boolean;
  startedAt: string; // Instant
}

export interface EnrollmentsResponse {
  enrollments: EnrollmentResponse[];
  activeLanguageId: Uuid | null;
}

export type LessonStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED';

export interface LessonListItemResponse {
  id: Uuid;
  orderIndex: number;
  title: string;
  status: LessonStatus;
  unlocked: boolean;
}

export type ExerciseType =
  | 'MULTIPLE_CHOICE'
  | 'TRUE_FALSE'
  | 'MATCHING'
  | 'ODD_ONE_OUT'
  | 'ERROR_FINDING'
  | 'TRANSLATION'
  | 'AI_SCENARIO';

export type ExerciseDifficulty = 'EASY' | 'MEDIUM' | 'HARD';

export interface ExerciseMetadataResponse {
  exerciseId: Uuid;
  type: ExerciseType;
  difficulty: ExerciseDifficulty;
  prompt: string;
  orderIndex: number;
  requiredForCompletion: boolean;
  duelEligible: boolean;
  maxPoints: number;
}

export interface LessonDetailResponse {
  lessonId: Uuid;
  orderIndex: number;
  title: string;
  theoryMarkdown: string;
  exercises: ExerciseMetadataResponse[];
}

// --- Exercise content (presentation, no solution), discriminated on `type` ---

export interface OptionItem {
  id: string;
  text: string;
}

export interface MultipleChoiceContent {
  type: 'MULTIPLE_CHOICE';
  multiSelect: boolean;
  options: OptionItem[];
}

export interface TrueFalseContent {
  type: 'TRUE_FALSE';
  statement: string;
}

export interface MatchingContent {
  type: 'MATCHING';
  leftItems: OptionItem[];
  rightItems: OptionItem[];
}

export interface OddOneOutContent {
  type: 'ODD_ONE_OUT';
  items: OptionItem[];
}

export interface ErrorFindingToken {
  index: number;
  text: string;
}

export interface ErrorFindingContent {
  type: 'ERROR_FINDING';
  tokens: ErrorFindingToken[];
}

export interface TranslationContent {
  type: 'TRANSLATION';
  sourceText: string;
  targetLanguageCode: string;
  rubric: string[];
}

export interface AiScenarioContent {
  type: 'AI_SCENARIO';
  role: string;
  situation: string;
  goals: string[];
  targetLanguageCode: string;
  openingMessage: string;
}

export type ExerciseContent =
  | MultipleChoiceContent
  | TrueFalseContent
  | MatchingContent
  | OddOneOutContent
  | ErrorFindingContent
  | TranslationContent
  | AiScenarioContent;

export interface ExercisePresentationResponse {
  exerciseId: Uuid;
  type: ExerciseType;
  difficulty: ExerciseDifficulty;
  prompt: string;
  maxPoints: number;
  requiredForCompletion: boolean;
  duelEligible: boolean;
  content: ExerciseContent;
}

// --- Answer payloads (the `answer` field of an attempt body), by type ---

export interface MultipleChoiceAnswer {
  selectedOptionIds: string[];
}

export interface TrueFalseAnswer {
  answer: boolean;
}

export interface MatchingPair {
  leftId: string;
  rightId: string;
}

export interface MatchingAnswer {
  pairs: MatchingPair[];
}

export interface OddOneOutAnswer {
  selectedItemId: string;
}

export interface ErrorFindingAnswer {
  markedTokenIndexes: number[];
}

export interface TranslationAnswer {
  text: string;
}

export type ExerciseAnswer =
  | MultipleChoiceAnswer
  | TrueFalseAnswer
  | MatchingAnswer
  | OddOneOutAnswer
  | ErrorFindingAnswer
  | TranslationAnswer;

export interface AttemptRequest {
  answer: ExerciseAnswer;
}

export type PointAwardStatus =
  | 'NOT_APPLICABLE'
  | 'PENDING'
  | 'AWARDED'
  | 'FAILED_RETRYABLE';

export interface AttemptResponse {
  attemptId: Uuid;
  scoreRatio: number;
  correct: boolean;
  feedback: string;
  pointAwardStatus: PointAwardStatus;
}

// --- Daily challenge ---

export type ProgressStatus = LessonStatus;

export interface DailyOwnStatus {
  status: ProgressStatus;
  correctCount: number;
  bonusAwarded: boolean;
}

export interface DailyChallengeTodayResponse {
  challengeId: Uuid;
  date: string; // yyyy-MM-dd
  languageId: Uuid;
  exercises: ExercisePresentationResponse[];
  ownStatus: DailyOwnStatus;
}

// --- AI scenario ---

export interface ScenarioStartResponse {
  sessionId: Uuid;
  attemptId: Uuid;
  openingMessage: string;
}

export interface ScenarioMessageResponse {
  aiMessage: string;
  turnCount: number;
}

export interface ScenarioCompleteResponse {
  scoreRatio: number;
  completed: boolean;
  points: number;
  feedback: string;
}
