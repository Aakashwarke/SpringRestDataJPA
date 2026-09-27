import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { api, setSessionLostHandler, tokens } from '../api/client';
import type { MeResponse, OrgRole, TokenResponse } from '../api/types';

interface AuthState {
  user: MeResponse | null;
  role: OrgRole | null;
  /** True until the first session restore attempt finishes, so guards don't flash. */
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (input: RegisterInput) => Promise<void>;
  logout: () => Promise<void>;
  switchOrganization: (organizationId: string) => Promise<void>;
  refreshUser: () => Promise<void>;
}

export interface RegisterInput {
  email: string;
  password: string;
  fullName: string;
  organizationName: string;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<MeResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const loadUser = useCallback(async () => {
    const me = await api.get<MeResponse>('/auth/me');
    setUser(me);
  }, []);

  /**
   * On a page load the access token is gone (it was only ever in memory), but the
   * refresh token may still be in storage. Calling /auth/me lets the client's 401
   * handler exchange it, which restores the session in one round trip.
   */
  useEffect(() => {
    let cancelled = false;

    setSessionLostHandler(() => {
      if (!cancelled) setUser(null);
    });

    (async () => {
      if (!tokens.hasSession) {
        setLoading(false);
        return;
      }
      try {
        await loadUser();
      } catch {
        tokens.clear();
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [loadUser]);

  const login = useCallback(
    async (email: string, password: string) => {
      const response = await api.post<TokenResponse>('/auth/login', { email, password }, true);
      tokens.set(response.accessToken, response.refreshToken);
      await loadUser();
    },
    [loadUser],
  );

  const register = useCallback(
    async (input: RegisterInput) => {
      const response = await api.post<TokenResponse>('/auth/register', input, true);
      tokens.set(response.accessToken, response.refreshToken);
      await loadUser();
    },
    [loadUser],
  );

  const logout = useCallback(async () => {
    try {
      await api.post<void>('/auth/logout');
    } catch {
      // Revoking server-side is best effort; the local session goes either way.
    }
    tokens.clear();
    setUser(null);
  }, []);

  const switchOrganization = useCallback(
    async (organizationId: string) => {
      const response = await api.post<TokenResponse>('/auth/switch-organization', { organizationId });
      // No new refresh token is issued here, so only the access token changes.
      tokens.set(response.accessToken, response.refreshToken);
      await loadUser();
    },
    [loadUser],
  );

  const value = useMemo<AuthState>(
    () => ({
      user,
      role: user?.activeRole ?? null,
      loading,
      login,
      register,
      logout,
      switchOrganization,
      refreshUser: loadUser,
    }),
    [user, loading, login, register, logout, switchOrganization, loadUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}

/** Ranked exactly like the backend's OrgRole, so the UI hides what the API would refuse. */
const RANK: Record<OrgRole, number> = { MEMBER: 0, ADMIN: 1, OWNER: 2 };

export function atLeast(role: OrgRole | null, required: OrgRole): boolean {
  if (!role) return false;
  return RANK[role] >= RANK[required];
}
