// TanStack Query hooks for the social feature (see the learning feature hooks
// for the useQuery/useMutation + invalidation pattern). Query keys are stable
// arrays so mutations can invalidate precisely.
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  acceptDuel,
  acceptFriendRequest,
  createDuel,
  getDuel,
  getDuelExercises,
  getFriendsLeaderboard,
  getNotificationSummary,
  listDuels,
  listFriendRequests,
  listFriends,
  newIdempotencyKey,
  rejectDuel,
  rejectFriendRequest,
  removeFriend,
  getPublicProfile,
  searchUsers,
  sendFriendRequest,
  submitDuelAnswer,
} from './api';
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

// Poll the notification summary every 15 seconds (baseline decision D15).
const NOTIFICATION_POLL_MS = 15_000;

// --- Query keys ---
export const friendsQueryKey = ['social', 'friends'] as const;
export const friendsLeaderboardQueryKey = ['social', 'leaderboard', 'friends'] as const;
export const duelsQueryKey = ['social', 'duels'] as const;
export const notificationSummaryQueryKey = ['social', 'notifications'] as const;

export function friendRequestsQueryKey(
  direction: FriendRequestDirection,
  status: FriendRequestStatus,
) {
  return ['social', 'friend-requests', direction, status] as const;
}

export function duelQueryKey(id: Uuid | null | undefined) {
  return ['social', 'duel', id] as const;
}

export function duelExercisesQueryKey(id: Uuid | null | undefined) {
  return ['social', 'duel', id, 'exercises'] as const;
}

// --- Friends ---
export function useFriends(page = 0, size = 20) {
  return useQuery<PageResponse<FriendView>>({
    queryKey: [...friendsQueryKey, page, size],
    queryFn: () => listFriends(page, size),
  });
}

export function useRemoveFriend() {
  const queryClient = useQueryClient();
  return useMutation<void, Error, Uuid>({
    mutationFn: (friendId: Uuid) => removeFriend(friendId, newIdempotencyKey()),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: friendsQueryKey });
      void queryClient.invalidateQueries({ queryKey: friendsLeaderboardQueryKey });
    },
  });
}

// --- Friend requests ---
export function useFriendRequests(
  direction: FriendRequestDirection,
  status: FriendRequestStatus,
  page = 0,
  size = 20,
) {
  return useQuery<PageResponse<FriendRequestView>>({
    queryKey: [...friendRequestsQueryKey(direction, status), page, size],
    queryFn: () => listFriendRequests(direction, status, page, size),
  });
}

export function useSendFriendRequest() {
  const queryClient = useQueryClient();
  return useMutation<FriendRequestView, Error, Uuid>({
    mutationFn: (receiverId: Uuid) =>
      sendFriendRequest(receiverId, newIdempotencyKey()),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['social', 'friend-requests'] });
    },
  });
}

export function useAcceptFriendRequest() {
  const queryClient = useQueryClient();
  return useMutation<void, Error, Uuid>({
    mutationFn: (id: Uuid) => acceptFriendRequest(id, newIdempotencyKey()),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['social', 'friend-requests'] });
      void queryClient.invalidateQueries({ queryKey: friendsQueryKey });
      void queryClient.invalidateQueries({ queryKey: notificationSummaryQueryKey });
    },
  });
}

export function useRejectFriendRequest() {
  const queryClient = useQueryClient();
  return useMutation<void, Error, Uuid>({
    mutationFn: (id: Uuid) => rejectFriendRequest(id, newIdempotencyKey()),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['social', 'friend-requests'] });
      void queryClient.invalidateQueries({ queryKey: notificationSummaryQueryKey });
    },
  });
}

// --- Friends leaderboard ---
export function useFriendsLeaderboard() {
  return useQuery<LeaderboardEntryView[]>({
    queryKey: friendsLeaderboardQueryKey,
    queryFn: getFriendsLeaderboard,
  });
}

// --- Duels ---
export function useDuels(status?: DuelStatus) {
  return useQuery<PageResponse<DuelSummaryView>>({
    queryKey: [...duelsQueryKey, status ?? 'ALL'],
    queryFn: () => listDuels(status),
  });
}

export function useDuel(id: Uuid | undefined) {
  return useQuery<DuelDetailView>({
    queryKey: duelQueryKey(id),
    queryFn: () => getDuel(id as Uuid),
    enabled: Boolean(id),
  });
}

export function useDuelExercises(id: Uuid | undefined, enabled: boolean) {
  return useQuery<DuelExerciseView[]>({
    queryKey: duelExercisesQueryKey(id),
    queryFn: () => getDuelExercises(id as Uuid),
    enabled: Boolean(id) && enabled,
  });
}

export function useCreateDuel() {
  const queryClient = useQueryClient();
  return useMutation<DuelView, Error, CreateDuelBody>({
    mutationFn: (body: CreateDuelBody) => createDuel(body, newIdempotencyKey()),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: duelsQueryKey });
    },
  });
}

export function useAcceptDuel() {
  const queryClient = useQueryClient();
  return useMutation<void, Error, Uuid>({
    mutationFn: (id: Uuid) => acceptDuel(id, newIdempotencyKey()),
    onSuccess: (_data, id) => {
      void queryClient.invalidateQueries({ queryKey: duelsQueryKey });
      void queryClient.invalidateQueries({ queryKey: duelQueryKey(id) });
      void queryClient.invalidateQueries({ queryKey: notificationSummaryQueryKey });
    },
  });
}

export function useRejectDuel() {
  const queryClient = useQueryClient();
  return useMutation<void, Error, Uuid>({
    mutationFn: (id: Uuid) => rejectDuel(id, newIdempotencyKey()),
    onSuccess: (_data, id) => {
      void queryClient.invalidateQueries({ queryKey: duelsQueryKey });
      void queryClient.invalidateQueries({ queryKey: duelQueryKey(id) });
      void queryClient.invalidateQueries({ queryKey: notificationSummaryQueryKey });
    },
  });
}

interface SubmitDuelAnswerVars {
  position: number;
  answer: Record<string, unknown>;
}

export function useSubmitDuelAnswer(duelId: Uuid) {
  const queryClient = useQueryClient();
  return useMutation<AnswerResultView, Error, SubmitDuelAnswerVars>({
    mutationFn: ({ position, answer }: SubmitDuelAnswerVars) =>
      submitDuelAnswer(duelId, position, answer, newIdempotencyKey()),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: duelQueryKey(duelId) });
      void queryClient.invalidateQueries({ queryKey: duelsQueryKey });
      void queryClient.invalidateQueries({ queryKey: notificationSummaryQueryKey });
    },
  });
}

// --- Notifications (15s polling) ---
export function useNotificationSummary(enabled = true) {
  return useQuery<NotificationSummaryView>({
    queryKey: notificationSummaryQueryKey,
    queryFn: getNotificationSummary,
    enabled,
    refetchInterval: enabled ? NOTIFICATION_POLL_MS : false,
    refetchIntervalInBackground: false,
  });
}

// --- Username search (add-friend) ---
export function useUserSearch(username: string) {
  const trimmed = username.trim();
  return useQuery<PublicProfile[]>({
    queryKey: ['social', 'user-search', trimmed],
    queryFn: () => searchUsers(trimmed),
    enabled: trimmed.length >= 2,
  });
}

// Resolves a set of user UUIDs to their public profiles (display names), used by
// the request/duel screens where the social service returns only ids. Each id
// is cached individually under its own query key, so a given user is fetched
// once and reused across screens. Missing/failed lookups simply stay absent
// from the returned map, and the UI falls back to a short-id label.
const USER_PROFILE_STALE_MS = 5 * 60_000;

export function userProfileQueryKey(id: Uuid) {
  return ['social', 'user-profile', id] as const;
}

export function useUserProfiles(ids: Uuid[]): Map<Uuid, PublicProfile> {
  // Dedupe so the same id is not fetched twice within one screen.
  const uniqueIds = Array.from(new Set(ids.filter((id): id is Uuid => Boolean(id))));

  const results = useQueries({
    queries: uniqueIds.map((id) => ({
      queryKey: userProfileQueryKey(id),
      queryFn: () => getPublicProfile(id),
      staleTime: USER_PROFILE_STALE_MS,
      // A failed lookup should not spam retries; the UI degrades gracefully.
      retry: false,
    })),
  });

  const map = new Map<Uuid, PublicProfile>();
  results.forEach((result, index) => {
    if (result.data) {
      map.set(uniqueIds[index], result.data);
    }
  });
  return map;
}
