import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { AuditEntry, Page } from '../api/types';
import { PageHeader } from '../components/Layout';
import { Card, Empty, ErrorNotice } from '../components/ui';

export function AuditLog() {
  const [page, setPage] = useState<Page<AuditEntry> | null>(null);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    (async () => {
      try {
        setPage(await api.get<Page<AuditEntry>>('/audit-logs?size=100'));
      } catch (caught) {
        setError(caught);
      }
    })();
  }, []);

  return (
    <>
      <PageHeader title="Audit log" />

      <div className="content">
        <ErrorNotice error={error} />

        <Card flush>
          <div className="table-wrap">
            {!page ? (
              <p className="skeleton" style={{ paddingLeft: 20 }}>
                Loading…
              </p>
            ) : page.items.length === 0 ? (
              <Empty>Nothing recorded yet.</Empty>
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>When</th>
                    <th>Action</th>
                    <th>Target</th>
                    <th>Detail</th>
                  </tr>
                </thead>
                <tbody>
                  {page.items.map((entry, index) => (
                    <tr key={`${entry.createdAt}-${index}`}>
                      <td className="mono" style={{ whiteSpace: 'nowrap', color: 'var(--ink-soft)' }}>
                        {new Date(entry.createdAt).toLocaleString()}
                      </td>
                      <td className="mono">{entry.action}</td>
                      <td className="mono" style={{ color: 'var(--ink-soft)' }}>{entry.target ?? '—'}</td>
                      <td style={{ color: 'var(--ink-soft)' }}>{entry.detail ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </Card>

        <p className="card-sub">
          Written in the same transaction as the action it records, so if the action rolls back the
          claim that it happened rolls back with it.
        </p>
      </div>
    </>
  );
}
