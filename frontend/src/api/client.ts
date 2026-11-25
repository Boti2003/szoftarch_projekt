// Közös HTTP kliens. A base path /api/v1; a hívásokat az Nginx (compose) vagy
// a Vite dev proxy továbbítja a Gateway felé.
const API_BASE = '/api/v1';

export async function apiGet<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: { Accept: 'application/json' },
    credentials: 'include',
  });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status} – ${path}`);
  }
  return (await response.json()) as T;
}

export interface SystemHealth {
  status: string;
  gateway: string;
  identity: string;
  learning: string;
  social: string;
  ai: string;
}

export function fetchSystemHealth(): Promise<SystemHealth> {
  return apiGet<SystemHealth>('/system/health');
}

// --- Learning feature helpers (additive) ---

// RFC 9457 Problem Details body shape returned by the backend on errors.
export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  traceId?: string;
  fieldErrors?: Record<string, string>;
}

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
