// Session auth for the API. The backend sets an HttpOnly cookie on login, which the
// browser sends automatically with every same-origin fetch() and EventSource, so the
// rest of the app doesn't need to know about auth at all.

const AUTH_BASE = '/api/v1/auth';

/** Fired whenever an API call comes back 401, e.g. after the session expires. */
export const AUTH_REQUIRED_EVENT = 'stockpulse:auth-required';

export interface AuthStatus {
  enabled: boolean;
  authenticated: boolean;
}

export const getAuthStatus = async (): Promise<AuthStatus> => {
  const response = await fetch(`${AUTH_BASE}/status`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export type LoginResult =
  | { ok: true }
  | { ok: false; message: string };

export const login = async (password: string): Promise<LoginResult> => {
  const response = await fetch(`${AUTH_BASE}/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ password }),
  });
  if (response.ok) return { ok: true };
  if (response.status === 401) return { ok: false, message: 'Incorrect password.' };
  if (response.status === 429) {
    const minutes = Math.ceil(Number(response.headers.get('Retry-After') || 900) / 60);
    return { ok: false, message: `Too many attempts. Try again in ${minutes} min.` };
  }
  if (response.status === 503) return { ok: false, message: 'Sign-in is not configured on the server (APP_PASSWORD).' };
  return { ok: false, message: `Sign-in failed (HTTP ${response.status}).` };
};

export const logout = async (): Promise<void> => {
  await fetch(`${AUTH_BASE}/logout`, { method: 'POST' });
};

let installed = false;

/** Wraps window.fetch once so any 401 from the API asks the user to sign in again. */
export const installAuthInterceptor = (): void => {
  if (installed) return;
  installed = true;
  const originalFetch = window.fetch.bind(window);
  window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
    const response = await originalFetch(input, init);
    if (response.status === 401) {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
      if (url.includes('/api/v1/') && !url.includes('/api/v1/auth/')) {
        window.dispatchEvent(new Event(AUTH_REQUIRED_EVENT));
      }
    }
    return response;
  };
};
