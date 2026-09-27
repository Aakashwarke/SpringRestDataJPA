import type { ReactNode } from 'react';
import { ApiError } from '../api/client';
import { UNLIMITED } from '../api/types';

export function Card({
  title,
  subtitle,
  actions,
  children,
  flush,
}: {
  title?: string;
  subtitle?: string;
  actions?: ReactNode;
  children: ReactNode;
  flush?: boolean;
}) {
  return (
    <section className={flush ? 'card flush' : 'card'}>
      {(title || actions) && (
        <div className="card-head" style={flush ? { padding: '18px 20px 0' } : undefined}>
          <div>
            {title && <h2>{title}</h2>}
            {subtitle && <p className="card-sub">{subtitle}</p>}
          </div>
          <div className="spacer" />
          {actions}
        </div>
      )}
      {children}
    </section>
  );
}

export function Stat({
  label,
  value,
  sub,
  meter,
}: {
  label: string;
  value: string;
  sub?: string;
  meter?: { used: number; limit: number };
}) {
  const pct =
    meter && meter.limit !== UNLIMITED && meter.limit > 0
      ? Math.min(100, Math.round((meter.used / meter.limit) * 100))
      : null;

  return (
    <div className="card stat">
      <p className="k">{label}</p>
      <p className="v num">{value}</p>
      {sub && <p className="sub">{sub}</p>}
      {pct !== null && (
        <div className="meter">
          <span
            className={pct >= 100 ? 'full' : pct >= 80 ? 'warn' : undefined}
            style={{ width: `${pct}%` }}
          />
        </div>
      )}
    </div>
  );
}

export function Badge({ children, tone }: { children: ReactNode; tone?: 'leaf' | 'signal' | 'danger' }) {
  return <span className={tone ? `badge ${tone}` : 'badge'}>{children}</span>;
}

/**
 * Renders any API failure. A 402 gets the upgrade treatment, using the limit and
 * current values the backend supplies rather than text guessed in the UI.
 */
export function ErrorNotice({ error, onUpgrade }: { error: unknown; onUpgrade?: () => void }) {
  if (!error) return null;

  if (error instanceof ApiError) {
    const quota = error.quota;
    if (quota) {
      return (
        <div className="notice quota">
          <p>
            <strong>Plan limit reached.</strong> Your {quota.plan} plan allows{' '}
            <span className="num">{quota.limit === UNLIMITED ? 'unlimited' : quota.limit}</span>{' '}
            {quota.metric.replace(/_/g, ' ')}, and you are using{' '}
            <span className="num">{quota.current}</span>.
          </p>
          {onUpgrade && (
            <div>
              <button type="button" className="btn sm" onClick={onUpgrade}>
                See plans
              </button>
            </div>
          )}
        </div>
      );
    }

    if (error.fieldErrors) {
      return (
        <div className="notice error">
          <p>
            <strong>Please check the form.</strong>
          </p>
          <ul style={{ margin: 0, paddingLeft: '1.1em' }}>
            {Object.entries(error.fieldErrors).map(([field, message]) => (
              <li key={field}>
                {field}: {message}
              </li>
            ))}
          </ul>
        </div>
      );
    }

    return (
      <div className="notice error">
        <p>{error.message}</p>
      </div>
    );
  }

  return (
    <div className="notice error">
      <p>Something went wrong. Please try again.</p>
    </div>
  );
}

export function Modal({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  return (
    <div
      className="backdrop"
      role="dialog"
      aria-modal="true"
      aria-label={title}
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div className="modal">
        <h2>{title}</h2>
        {children}
      </div>
    </div>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <p className="empty">{children}</p>;
}

export function formatDate(iso?: string): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

export function formatCount(value: number): string {
  if (value === UNLIMITED) return 'Unlimited';
  return value.toLocaleString();
}

export function formatPrice(cents: number): string {
  if (cents === 0) return 'Free';
  return `$${(cents / 100).toFixed(0)}`;
}
