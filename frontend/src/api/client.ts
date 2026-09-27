import type { ApiErrorBody, QuotaDetails } from './types';

const BASE = '/api/v1';
const REFRESH_STORAGE_KEY = 'springlaunch.refreshToken';

/**
 * Every non-2xx response becomes one of these, so callers branch on a stable
 * `code` rather than on status numbers scattered through the UI.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors?: Record<string, string>;
  readonly details?: Record<string, unknown>;

  constructor(status: number, body: ApiErrorBody) {
    super(body.message);
    this.name = 'ApiError';
    this.status = status;
    this.code = body.code;
    this.fieldErrors = body.fieldErrors;
    this.details = body.details;
  }

  /** A plan limit was reached. `quota` carries what the upgrade prompt needs. */
  get isQuota(): boolean {
    return this.status === 402 && this.code === 'quota_exceeded';
  }

  get quota(): QuotaDetails | null {
    if (!this.isQuota || !this.details) return null;
    return this.details as unknown as QuotaDetails;
  }
}

/**
 * The access token is held in memory only. Keeping it out of localStorage means a
 * cross-site script cannot read it from storage, and its 15-minute lifetime bounds
 * the damage if it leaks another way.
 */
let accessToken: string | null = null;

/**
 * The refresh token does have to survive a page reload, so it lives in localStorage.
 * That is a deliberate, documented trade-off: it is readable by any script running on
 * the page. The stronger option is an httpOnly, SameSite cookie issued by the backend,
 * which requires a cookie-based auth endpoint and CSRF protection. See docs/FRONTEND.md.
 */
function readRefreshToken(): string | null {
  try {
    return localStorage.getItem(REFRESH_STORAGE_KEY);
  } catch {
    return null;
  }
}

function writeRefreshToken(token: string | null): void {
  try {
    if (token) localStorage.setItem(REFRESH_STORAGE_KEY, token);
    else localStorage.removeItem(REFRESH_STORAGE_KEY);
  } catch {
    // Private browsing or blocked storage. The session still works until reload.
  }
}

export const tokens = {
  get access(): string | null {
    return accessToken;
  },
  get refresh(): string | null {
    return readRefreshToken();
  },
  set(access: string, refresh?: string): void {
    accessToken = access;
    if (refresh) writeRefreshToken(refresh);
  },
  clear(): void {
    accessToken = null;
    writeRefreshToken(null);
  },
  get hasSession(): boolean {
    return accessToken !== null || readRefreshToken() !== null;
  },
};

let onSessionLost: (() => void) | null = null;

/** Lets the auth provider redirect to sign-in when the session can no longer be renewed. */
export function setSessionLostHandler(handler: () => void): void {
  onSessionLost = handler;
}

async function parseError(response: Response): Promise<ApiError> {
  let body: ApiErrorBody;
  try {
    body = (await response.json()) as ApiErrorBody;
  } catch {
    body = {
      code: 'unexpected',
      message: `Request failed with status ${response.status}`,
      timestamp: new Date().toISOString(),
    };
  }
  return new ApiError(response.status, body);
}

/**
 * Refresh tokens are single-use, so two requests refreshing at once would race and one
 * would lose its token. Sharing one in-flight promise means a burst of 401s triggers
 * exactly one refresh.
 */
let refreshInFlight: Promise<boolean> | null = null;

async function refreshSession(): Promise<boolean> {
  if (refreshInFlight) return refreshInFlight;

  const refreshToken = readRefreshToken();
  if (!refreshToken) return false;

  refreshInFlight = (async () => {
    try {
      const response = await fetch(`${BASE}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
      if (!response.ok) {
        tokens.clear();
        return false;
      }
      const body = (await response.json()) as { accessToken: string; refreshToken?: string };
      tokens.set(body.accessToken, body.refreshToken);
      return true;
    } catch {
      return false;
    } finally {
      refreshInFlight = null;
    }
  })();

  return refreshInFlight;
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  /** Set for sign-in and sign-up, which must not attempt a refresh on 401. */
  anonymous?: boolean;
}

async function send(path: string, options: RequestOptions, retrying = false): Promise<Response> {
  const headers: Record<string, string> = {};
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  if (!options.anonymous && accessToken) headers['Authorization'] = `Bearer ${accessToken}`;

  const response = await fetch(`${BASE}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  if (response.status === 401 && !options.anonymous && !retrying) {
    if (await refreshSession()) return send(path, options, true);
    tokens.clear();
    onSessionLost?.();
  }

  return response;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(path, options);
  if (!response.ok) throw await parseError(response);
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown, anonymous = false) =>
    request<T>(path, { method: 'POST', body, anonymous }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  del: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
};
