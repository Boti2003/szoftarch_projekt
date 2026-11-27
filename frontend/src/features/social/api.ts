// One function per social-service endpoint (see the learning feature api.ts for
// the apiGet/apiSend convention). Retryable mutations send an Idempotency-Key.
import { apiGet, apiSend } from '../../api/client';
import type {
  AnswerResultView,
  CreateDuelBody,
  DuelDetailView,
  DuelExerciseView,
  DuelStatus,
  DuelSummaryView,
  DuelView,
  FriendRequestDirection,
  FriendRequestStatus,
  FriendRequestView,
  FriendView,
  LeaderboardEntryView,
  NotificationSummaryView,
  PageResponse,
  PublicProfile,
  Uuid,
} from './types';

// Idempotency-Key (UUID) for every retryable mutation.
export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

// --- Friend requests ---
export function sendFriendRequest(
  receiverId: Uuid,
  idempotencyKey: string,
): Promise<FriendRequestView> {
  return apiSend<FriendRequestView>('POST', '/friend-requests', {
    body: { receiverId },
    idempotencyKey,
  });
}

export function listFriendRequests(
  direction: FriendRequestDirection,
  status: FriendRequestStatus,
  page = 0,
  size = 20,
): Promise<PageResponse<FriendRequestView>> {
  const params = new URLSearchParams({
    direction,
    status,
    page: String(page),
    size: String(size),
  });
  return apiGet<PageResponse<FriendRequestView>>(`/friend-requests?${params}`);
}

export function acceptFriendRequest(id: Uuid, idempotencyKey: string): Promise<void> {
  return apiSend<void>('POST', `/friend-requests/${id}/accept`, { idempotencyKey });
}

export function rejectFriendRequest(id: Uuid, idempotencyKey: string): Promise<void> {
  return apiSend<void>('POST', `/friend-requests/${id}/reject`, { idempotencyKey });
}

// --- Friends ---
export function listFriends(page = 0, size = 20): Promise<PageResponse<FriendView>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return apiGet<PageResponse<FriendView>>(`/friends?${params}`);
}

export function removeFriend(friendId: Uuid, idempotencyKey: string): Promise<void> {
  return apiSend<void>('DELETE', `/friends/${friendId}`, { idempotencyKey });
}

// --- Friends leaderboard ---
export function getFriendsLeaderboard(): Promise<LeaderboardEntryView[]> {
  return apiGet<LeaderboardEntryView[]>('/leaderboards/friends');
}

// --- Duels ---
export function createDuel(
  body: CreateDuelBody,
  idempotencyKey: string,
): Promise<DuelView> {
  return apiSend<DuelView>('POST', '/duels', { body, idempotencyKey });
}

export function listDuels(status?: DuelStatus): Promise<DuelSummaryView[]> {
  const query = status ? `?status=${status}` : '';
  return apiGet<DuelSummaryView[]>(`/duels${query}`);
}

export function getDuel(id: Uuid): Promise<DuelDetailView> {
  return apiGet<DuelDetailView>(`/duels/${id}`);
}

export function acceptDuel(id: Uuid, idempotencyKey: string): Promise<void> {
  return apiSend<void>('POST', `/duels/${id}/accept`, { idempotencyKey });
}

export function rejectDuel(id: Uuid, idempotencyKey: string): Promise<void> {
  return apiSend<void>('POST', `/duels/${id}/reject`, { idempotencyKey });
}

export function getDuelExercises(id: Uuid): Promise<DuelExerciseView[]> {
  return apiGet<DuelExerciseView[]>(`/duels/${id}/exercises`);
}

export function submitDuelAnswer(
  id: Uuid,
  exerciseId: Uuid,
  answer: string,
  idempotencyKey: string,
): Promise<AnswerResultView> {
  return apiSend<AnswerResultView>('POST', `/duels/${id}/answers`, {
    body: { exerciseId, answer },
    idempotencyKey,
  });
}

// --- Notifications ---
export function getNotificationSummary(): Promise<NotificationSummaryView> {
  return apiGet<NotificationSummaryView>('/notifications/summary');
}

// --- Identity username search (add-friend flow) ---
export function searchUsers(username: string): Promise<PublicProfile[]> {
  return apiGet<PublicProfile[]>(`/users/search?username=${encodeURIComponent(username)}`);
}
