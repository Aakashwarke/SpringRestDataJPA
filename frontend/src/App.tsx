import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { Layout } from './components/Layout';
import { ApiKeys } from './pages/ApiKeys';
import { AuditLog } from './pages/AuditLog';
import { Billing } from './pages/Billing';
import { Dashboard } from './pages/Dashboard';
import { Login } from './pages/Login';
import { Members } from './pages/Members';
import { Projects } from './pages/Projects';
import { Register } from './pages/Register';

export default function App() {
  const { user, loading } = useAuth();

  // Waiting for the session-restore attempt, so the guard below doesn't bounce a
  // signed-in user to the login screen on every page load.
  if (loading) {
    return (
      <div className="auth-page">
        <p className="skeleton">Loading…</p>
      </div>
    );
  }

  if (!user) {
    return (
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }

  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Dashboard />} />
        <Route path="/projects" element={<Projects />} />
        <Route path="/members" element={<Members />} />
        <Route path="/api-keys" element={<ApiKeys />} />
        <Route path="/billing" element={<Billing />} />
        <Route path="/audit" element={<AuditLog />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
