// Közös HTTP kliens és típusok a perembiztonsághoz és auth-hoz.
const API_BASE = '/api/v1';

let inMemoryToken: string | null = null;
let onUnauthorizedCallback: (() => void) | null = null;

export const setAccessToken = (token: string | null) => {
  inMemoryToken = token;
};

export const getAccessToken = () => inMemoryToken;

export const setOnUnauthorized = (callback: (() => void) | null) => {
  onUnauthorizedCallback = callback;
};

export interface FieldError {
  field: string;
  message: string;
}

export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  fieldErrors?: FieldError[];
}

export interface UserSummary {
  id: string;
  email: string;
  username: string;
  displayName: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: UserSummary;
}

export interface UserProfile {
  id: string;
  email: string;
  username: string;
  displayName: string;
  totalPoints: number;
  level: number;
  nextLevelPoints: number;
  createdAt: string;
}

export interface LeaderboardEntry {
  rank: number;
  userId: string;
  username: string;
  displayName: string;
  level: number;
  totalPoints: number;
}

export interface LeaderboardResponse {
  entries: LeaderboardEntry[];
  myRank: LeaderboardEntry | null;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SystemHealth {
  status: string;
  gateway: string;
  identity: string;
  learning: string;
  social: string;
  ai: string;
}

let refreshPromise: Promise<string | null> | null = null;

// Deduplikált csendes tokenmegújítás (megelőzi a párhuzamos refresh hívásokat és az RTR reuse triggerelést)
async function requestFreshToken(): Promise<string | null> {
  if (refreshPromise) {
    return refreshPromise;
  }
  refreshPromise = (async () => {
    try {
      const res = await fetch(`${API_BASE}/auth/refresh`, {
        method: 'POST',
        headers: { Accept: 'application/json' },
        credentials: 'include',
      });
      if (res.ok) {
        const data = (await res.json()) as AuthResponse;
        setAccessToken(data.accessToken);
        return data.accessToken;
      } else {
        setAccessToken(null);
        onUnauthorizedCallback?.();
        return null;
      }
    } catch {
      setAccessToken(null);
      onUnauthorizedCallback?.();
      return null;
    } finally {
      refreshPromise = null;
    }
  })();
  return refreshPromise;
}

// supports transparent token renewal
export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers || {});
  headers.set('Accept', 'application/json');

  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  if (inMemoryToken) {
    headers.set('Authorization', `Bearer ${inMemoryToken}`);
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
    credentials: 'include',
  });

  // Ha 401 és nem a refresh/login végpont bukott el, próbáljunk csendes frissítést
  if (response.status === 401 && path !== '/auth/refresh' && path !== '/auth/login') {
    const newToken = await requestFreshToken();
    if (newToken) {
      headers.set('Authorization', `Bearer ${newToken}`);
      const retryRes = await fetch(`${API_BASE}${path}`, {
        ...options,
        headers,
        credentials: 'include',
      });
      if (!retryRes.ok) throw await parseError(retryRes);
      if (retryRes.status === 204) return null as T;
      return (await retryRes.json()) as T;
    }
  }

  if (!response.ok) {
    throw await parseError(response);
  }

  if (response.status === 204) {
    return null as T;
  }

  return (await response.json()) as T;
}

async function parseError(response: Response): Promise<Error> {
  try {
    const errorJson = (await response.json()) as ProblemDetails;
    if (errorJson.fieldErrors && errorJson.fieldErrors.length > 0) {
      const fieldMsg = errorJson.fieldErrors.map(e => `${e.field}: ${e.message}`).join(', ');
      return new Error(`Validációs hiba (${fieldMsg})`);
    }
    return new Error(errorJson.detail || errorJson.title || `HTTP ${response.status}`);
  } catch {
    return new Error(`HTTP ${response.status}`);
  }
}

export function apiGet<T>(path: string): Promise<T> {
  return apiFetch<T>(path, { method: 'GET' });
}

export function fetchSystemHealth(): Promise<SystemHealth> {
  return apiGet<SystemHealth>('/system/health');
}
