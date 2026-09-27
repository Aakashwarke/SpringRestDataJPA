import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Card, ErrorNotice } from '../components/ui';

export function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', organizationName: '', email: '', password: '' });
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  function set(key: keyof typeof form) {
    return (event: React.ChangeEvent<HTMLInputElement>) =>
      setForm((current) => ({ ...current, [key]: event.target.value }));
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await register(form);
      navigate('/', { replace: true });
    } catch (caught) {
      setError(caught);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div>
          <h1>Create your workspace</h1>
          <p className="lede">
            One step: this creates your account and your organization, and puts you on the free plan.
          </p>
        </div>

        <ErrorNotice error={error} />

        <Card>
          <form className="stack" onSubmit={submit}>
            <div className="field">
              <label htmlFor="fullName">Your name</label>
              <input id="fullName" required value={form.fullName} onChange={set('fullName')} />
            </div>
            <div className="field">
              <label htmlFor="organizationName">Organization name</label>
              <input
                id="organizationName"
                required
                value={form.organizationName}
                onChange={set('organizationName')}
              />
            </div>
            <div className="field">
              <label htmlFor="email">Work email</label>
              <input
                id="email"
                type="email"
                autoComplete="email"
                required
                value={form.email}
                onChange={set('email')}
              />
            </div>
            <div className="field">
              <label htmlFor="password">Password</label>
              <input
                id="password"
                type="password"
                autoComplete="new-password"
                required
                minLength={10}
                value={form.password}
                onChange={set('password')}
              />
              <p className="hint">At least 10 characters.</p>
            </div>
            <button className="btn" type="submit" disabled={busy}>
              {busy ? 'Creating…' : 'Create workspace'}
            </button>
          </form>
        </Card>

        <p className="auth-foot">
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      </div>
    </div>
  );
}
