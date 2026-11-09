import React, { useState } from 'react';
import { login } from '../services/auth';

interface LoginProps {
  onSuccess: () => void;
  sessionExpired?: boolean;
}

const Login = ({ onSuccess, sessionExpired = false }: LoginProps) => {
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!password || submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      const result = await login(password);
      if (result.ok) {
        onSuccess();
      } else {
        setError(result.message);
        setPassword('');
      }
    } catch {
      setError('Could not reach the server.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="min-h-screen flex items-center justify-center px-4"
      style={{ background: 'var(--bg-primary)', color: 'var(--text-primary)' }}
    >
      <form onSubmit={handleSubmit} className="glass-card w-full max-w-sm p-8 space-y-5">
        <div>
          <h1 className="text-2xl font-bold">StockPulse</h1>
          <p className="mt-1 text-sm" style={{ color: 'var(--text-secondary)' }}>
            {sessionExpired ? 'Your session ended. Sign in again to continue.' : 'Sign in to continue.'}
          </p>
        </div>

        <div className="space-y-2">
          <label htmlFor="password" className="block text-sm" style={{ color: 'var(--text-secondary)' }}>
            Password
          </label>
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            autoFocus
            className="glass-input w-full"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? 'login-error' : undefined}
          />
        </div>

        {error && (
          <p id="login-error" role="alert" className="text-sm" style={{ color: 'var(--signal-bearish)' }}>
            {error}
          </p>
        )}

        <button type="submit" className="glass-button w-full" disabled={!password || submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </div>
  );
};

export default Login;
