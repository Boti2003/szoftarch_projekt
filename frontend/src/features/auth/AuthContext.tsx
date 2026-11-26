import { createContext, useContext, useEffect, useState, ReactNode } from 'react';
import { apiFetch, AuthResponse, setAccessToken, setOnUnauthorized, UserProfile } from '../../api/client';

interface AuthContextType {
  user: UserProfile | null;
  loading: boolean;
  login: (identifier: string, password: string) => Promise<void>;
  register: (email: string, username: string, displayName: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshProfile: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);

  const refreshProfile = async () => {
    try {
      const profile = await apiFetch<UserProfile>('/users/me');
      setUser(profile);
    } catch {
      setUser(null);
    }
  };

  useEffect(() => {
    // Ha bármelyik kérés során elbukik a tokenfrissítés, a user állapot azonnal null lesz
    setOnUnauthorized(() => {
      setUser(null);
    });

    // Initial silent refresh on app load
    apiFetch<AuthResponse>('/auth/refresh', { method: 'POST' })
      .then(async (res) => {
        setAccessToken(res.accessToken);
        await refreshProfile();
      })
      .catch(() => {
        setAccessToken(null);
        setUser(null);
      })
      .finally(() => setLoading(false));

    return () => setOnUnauthorized(null);
  }, []);

  const login = async (identifier: string, password: string) => {
    const res = await apiFetch<AuthResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ loginIdentifier: identifier, password }),
    });
    setAccessToken(res.accessToken);
    await refreshProfile();
  };

  const register = async (email: string, username: string, displayName: string, password: string) => {
    await apiFetch('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, username, displayName, password }),
    });
    await login(username, password);
  };

  const logout = async () => {
    try {
      await apiFetch('/auth/logout', { method: 'POST' });
    } finally {
      setAccessToken(null);
      setUser(null);
    }
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout, refreshProfile }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
