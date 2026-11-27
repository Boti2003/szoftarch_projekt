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

// RFC 9457 Problem Details. INTEGRÁCIÓS MERGE-DÖNTÉS: az Identity (A) és a
// Learning (B) külön ProblemDetails-t definiált; itt EGYESÍTVE. A fieldErrors
// a strukturáltabb FieldError[] (A verziója); a learning korábbi
// Record<string,string> helyett erre tér át (a hibakezelő UI úgysem használta
// kulcs szerint). A traceId a B oldaláról marad.
export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  traceId?: string;
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

// --- Learning feature helpers (additive) ---

// Typed error carrying the HTTP status and the Problem Details 'code'
// (e.g. 'ai_unavailable' on a 502) so callers can branch on it.
export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly problem?: ProblemDetails;

  constructor(status: number, problem?: ProblemDetails) {
    super(problem?.detail ?? problem?.title ?? `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.code = problem?.code;
    this.problem = problem;
  }
}

async function parseProblem(response: Response): Promise<ProblemDetails | undefined> {
  try {
    return (await response.json()) as ProblemDetails;
  } catch {
    return undefined;
  }
}

export interface ApiSendOptions {
  body?: unknown;
  idempotencyKey?: string;
}

// Sends a mutating request (POST/PUT/...) with JSON body and optional
// Idempotency-Key header. Returns the parsed JSON body, or undefined for
// empty responses (e.g. 204 No Content). Throws ApiError on non-2xx.
export async function apiSend<T>(
  method: string,
  path: string,
  options: ApiSendOptions = {},
): Promise<T> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
  };
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (options.idempotencyKey) {
    headers['Idempotency-Key'] = options.idempotencyKey;
  }
  // INTEGRÁCIÓS MERGE-DÖNTÉS: a learning mutáló hívások is az Identity (A)
  // Bearer-token modelljét használják (memóriában tartott access token),
  // nem puszta cookie-t. A token a setAccessToken-ből jön (AuthContext).
  const token = getAccessToken();
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    credentials: 'include',
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });

  if (!response.ok) {
    throw new ApiError(response.status, await parseProblem(response));
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  if (!text) {
    return undefined as T;
  }
  return JSON.parse(text) as T;
}
