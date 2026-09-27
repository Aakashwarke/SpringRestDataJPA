import { NavLink, Outlet } from 'react-router-dom';
import { atLeast, useAuth } from '../auth/AuthContext';
import { ThemeToggle } from './ThemeToggle';

export function Layout() {
  const { user, role, logout, switchOrganization } = useAuth();
  const organizations = user?.organizations ?? [];
  const active = organizations.find((org) => org.organizationId === user?.activeOrganizationId);

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="dot" aria-hidden="true" />
          SpringLaunch
        </div>

        <p className="nav-label">Workspace</p>
        <NavLink to="/" end className={navClass}>
          Overview
        </NavLink>
        <NavLink to="/projects" className={navClass}>
          Projects
        </NavLink>

        <p className="nav-label">Organization</p>
        <NavLink to="/members" className={navClass}>
          Members
        </NavLink>
        {atLeast(role, 'ADMIN') && (
          <NavLink to="/api-keys" className={navClass}>
            API keys
          </NavLink>
        )}
        <NavLink to="/billing" className={navClass}>
          Billing
        </NavLink>
        {atLeast(role, 'ADMIN') && (
          <NavLink to="/audit" className={navClass}>
            Audit log
          </NavLink>
        )}

        <div className="sidebar-foot">
          {organizations.length > 1 && (
            <div className="field">
              <label htmlFor="org-switch">Organization</label>
              <select
                id="org-switch"
                value={user?.activeOrganizationId ?? ''}
                onChange={(event) => void switchOrganization(event.target.value)}
              >
                {organizations.map((org) => (
                  <option key={org.organizationId} value={org.organizationId}>
                    {org.name}
                  </option>
                ))}
              </select>
            </div>
          )}
          <p className="who">
            {user?.user.fullName}
            <br />
            {active?.name ?? '—'} · {role}
          </p>
          <div style={{ display: 'flex', gap: 8 }}>
            <ThemeToggle />
            <button type="button" className="icon-btn" onClick={() => void logout()}>
              Sign out
            </button>
          </div>
        </div>
      </aside>

      <div className="main">
        <Outlet />
      </div>
    </div>
  );
}

function navClass({ isActive }: { isActive: boolean }): string {
  return isActive ? 'nav-item active' : 'nav-item';
}

export function PageHeader({ title, children }: { title: string; children?: React.ReactNode }) {
  return (
    <header className="topbar">
      <h1>{title}</h1>
      <div className="spacer" />
      {children}
    </header>
  );
}
