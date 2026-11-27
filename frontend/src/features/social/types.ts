// TypeScript types mirroring the social-service DTOs (contracts/openapi/social-api.yaml
// and the application-layer view records). Timestamps are ISO-8601 UTC strings;
// ids are UUID strings. Lists use the shared Page_Response envelope.

export type Uuid = string;

// --- Shared pagination envelope (matches social PageResponse) ---
export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// --- Friend requests ---
export type FriendRequestStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED';

export type FriendRequestDirection = 'incoming' | 'outgoing';

export interface FriendRequestView {
  id: Uuid;
  senderId: Uuid;
  receiverId: Uuid;
  status: FriendRequestStatus;
  createdAt: string;
  updatedAt: string;
}

// --- Friends ---
export interface FriendView {
  userId: Uuid;
  displayName: string | null;
  points: number;
  level: number;
  friendsSince: string;
}

// --- Friends leaderboard ---
export interface LeaderboardEntryView {
  userId: Uuid;
  displayName: string | null;
  points: number;
  level: number;
  rank: number;
}

// --- Duels ---
export type DuelStatus =
  | 'PENDING'
  | 'ACCEPTED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'REJECTED'
  | 'EXPIRED';

export type ParticipantStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED';

export interface DuelView {
  id: Uuid;
  challengerId: Uuid;
  opponentId: Uuid;
  languageId: Uuid;
  learningSetId: Uuid | null;
  status: DuelStatus;
  expiresAt: string;
}

export interface DuelSummaryView {
  id: Uuid;
  challengerId: Uuid;
  opponentId: Uuid;
  languageId: Uuid;
  status: DuelStatus;
  myStatus: ParticipantStatus;
  winnerId: Uuid | null;
  expiresAt: string;
}

export interface ParticipantResult {
  userId: Uuid;
  status: ParticipantStatus;
  score: number;
  answeredCount: number;
}

export interface DuelDetailView {
  id: Uuid;
  challengerId: Uuid;
  opponentId: Uuid;
  languageId: Uuid;
  status: DuelStatus;
  winnerId: Uuid | null;
  expiresAt: string;
  viewerId: Uuid;
  self: ParticipantResult;
  // Gated server-side: null until the viewer's own status is COMPLETED.
  opponent: ParticipantResult | null;
}

export interface DuelOption {
  id: string;
  text: string;
}

export interface DuelExerciseView {
  position: number;
  exerciseId: Uuid;
  type: string;
  prompt: string;
  options: DuelOption[];
  // Raw type-specific presentation content from Learning (statement, tokens,
  // leftItems/rightItems, items, options, ...). Shape depends on `type`.
  content: Record<string, unknown>;
  maxPoints: number;
}

export interface AnswerResultView {
  duelId: Uuid;
  correct: boolean;
  awardedPoints: number;
  newScore: number;
  answeredCount: number;
  participantStatus: ParticipantStatus;
  duelStatus: DuelStatus;
  winnerId: Uuid | null;
}

// --- Notifications ---
export interface PendingRequestView {
  requestId: Uuid;
  senderId: Uuid;
  createdAt: string;
}

export interface ActionableDuelView {
  duelId: Uuid;
  status: DuelStatus;
  opponentId: Uuid;
  expiresAt: string | null;
}

export interface NotificationSummaryView {
  pendingFriendRequestCount: number;
  pendingFriendRequests: PendingRequestView[];
  actionableDuelCount: number;
  actionableDuels: ActionableDuelView[];
}

// --- Request bodies ---
export interface SendFriendRequestBody {
  receiverId: Uuid;
}

export interface CreateDuelBody {
  opponentId: Uuid;
  languageId: Uuid;
}

export interface SubmitDuelAnswerBody {
  position: number;
  answer: Record<string, unknown>;
}

// --- Identity (for the add-friend username search) ---
// Mirrors identity-service PublicProfileDto (GET /users/search → List).
export interface PublicProfile {
  id: Uuid;
  username: string;
  displayName: string;
  level: number;
  totalPoints: number;
}
