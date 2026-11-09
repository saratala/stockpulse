import React, { useCallback, useEffect, useState } from 'react';
import Login from '../pages/Login';
import { AUTH_REQUIRED_EVENT, getAuthStatus } from '../services/auth';

type GateState = 'checking' | 'signed-in' | 'signed-out' | 'expired' | 'unreachable';

/** Shows the login screen until the API session is valid, then renders the app. */
const AuthGate = ({ children }: { children: React.ReactNode }) => {
  const [state, setState] = useState<GateState>('checking');

  const check = useCallback(async () => {
    try {
      const status = await getAuthStatus();
      setState(status.authenticated ? 'signed-in' : 'signed-out');
    } catch {
      setState('unreachable');
    }
  }, []);

  useEffect(() => {
    check();
  }, [check]);

  useEffect(() => {
    const onAuthRequired = () => setState((s) => (s === 'signed-in' ? 'expired' : s));
    window.addEventListener(AUTH_REQUIRED_EVENT, onAuthRequired);
    return () => window.removeEventListener(AUTH_REQUIRED_EVENT, onAuthRequired);
  }, []);

  const handleSignedIn = () => {
    if (state === 'expired') {
      // Restart pages and live price streams that failed while signed out
      window.location.reload();
    } else {
      setState('signed-in');
    }
  };

  if (state === 'signed-in') return <>{children}</>;
  if (state === 'signed-out' || state === 'expired') {
    return <Login onSuccess={handleSignedIn} sessionExpired={state === 'expired'} />;
  }

  return (
    <div
      className="min-h-screen flex flex-col items-center justify-center gap-4 px-4 text-center"
      style={{ background: 'var(--bg-primary)', color: 'var(--text-secondary)' }}
    >
      {state === 'checking' ? (
        <p>Loading…</p>
      ) : (
        <>
          <p>Can't reach the StockPulse backend.</p>
          <button type="button" className="glass-button" onClick={() => { setState('checking'); check(); }}>
            Retry
          </button>
        </>
      )}
    </div>
  );
};

export default AuthGate;
