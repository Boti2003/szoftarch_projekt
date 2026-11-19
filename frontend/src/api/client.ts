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
