import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { Page, Project } from '../api/types';
import { atLeast, useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/Layout';
import { Badge, Card, Empty, ErrorNotice, Modal, formatDate } from '../components/ui';

export function Projects() {
  const { role } = useAuth();
  const navigate = useNavigate();
  const [page, setPage] = useState<Page<Project> | null>(null);
  const [showArchived, setShowArchived] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [creating, setCreating] = useState(false);

  const load = useCallback(async () => {
    try {
      setPage(await api.get<Page<Project>>(`/projects?archived=${showArchived}&size=50`));
    } catch (caught) {
      setError(caught);
    }
  }, [showArchived]);

  useEffect(() => {
    void load();
  }, [load]);

  async function act(action: () => Promise<unknown>) {
    setError(null);
    try {
      await action();
      await load();
    } catch (caught) {
      setError(caught);
    }
  }

  return (
    <>
      <PageHeader title="Projects">
        <button type="button" className="btn secondary sm" onClick={() => setShowArchived((v) => !v)}>
          {showArchived ? 'Show active' : 'Show archived'}
        </button>
        <button type="button" className="btn" onClick={() => setCreating(true)}>
          New project
        </button>
      </PageHeader>

      <div className="content">
        {/* A 402 here renders as an upgrade prompt carrying the real limit numbers. */}
        <ErrorNotice error={error} onUpgrade={() => navigate('/billing')} />

        <Card flush>
          <div className="table-wrap">
            {!page ? (
              <p className="skeleton" style={{ paddingLeft: 20 }}>
                Loading…
              </p>
            ) : page.items.length === 0 ? (
              <Empty>{showArchived ? 'No archived projects.' : 'No projects yet.'}</Empty>
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Description</th>
                    <th>Created</th>
                    <th className="right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {page.items.map((project) => (
                    <tr key={project.id}>
                      <td>
                        <strong style={{ fontWeight: 600 }}>{project.name}</strong>
                        {project.archived && (
                          <>
                            {' '}
                            <Badge>Archived</Badge>
                          </>
                        )}
                      </td>
                      <td style={{ color: 'var(--ink-soft)' }}>{project.description || '—'}</td>
                      <td>{formatDate(project.createdAt)}</td>
                      <td className="right">
                        <div style={{ display: 'inline-flex', gap: 6 }}>
                          <button
                            type="button"
                            className="btn sm secondary"
                            onClick={() =>
                              void act(() =>
                                api.post(
                                  `/projects/${project.id}/${project.archived ? 'unarchive' : 'archive'}`,
                                ),
                              )
                            }
                          >
                            {project.archived ? 'Restore' : 'Archive'}
                          </button>
                          {atLeast(role, 'ADMIN') && (
                            <button
                              type="button"
                              className="btn sm danger"
                              onClick={() => void act(() => api.del(`/projects/${project.id}`))}
                            >
                              Delete
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </Card>

        {page && !showArchived && (
          <p className="card-sub">
            Archiving a project frees a slot on your plan. Restoring one has to fit within the limit again.
          </p>
        )}
      </div>

      {creating && (
        <CreateProject
          onClose={() => setCreating(false)}
          onCreated={() => {
            setCreating(false);
            void load();
          }}
          onError={setError}
        />
      )}
    </>
  );
}

function CreateProject({
  onClose,
  onCreated,
  onError,
}: {
  onClose: () => void;
  onCreated: () => void;
  onError: (error: unknown) => void;
}) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [busy, setBusy] = useState(false);
  const [localError, setLocalError] = useState<unknown>(null);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setLocalError(null);
    try {
      await api.post('/projects', { name, description: description || undefined });
      onCreated();
    } catch (caught) {
      // A quota failure belongs on the page behind the dialog, next to the plan context.
      if (caught && typeof caught === 'object' && 'isQuota' in caught && caught.isQuota) {
        onError(caught);
        onClose();
      } else {
        setLocalError(caught);
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title="New project" onClose={onClose}>
      <ErrorNotice error={localError} />
      <form className="stack" onSubmit={submit}>
        <div className="field">
          <label htmlFor="project-name">Name</label>
          <input
            id="project-name"
            required
            autoFocus
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="project-description">Description</label>
          <textarea
            id="project-description"
            rows={3}
            value={description}
            onChange={(event) => setDescription(event.target.value)}
          />
        </div>
        <div className="modal-actions">
          <button type="button" className="btn secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn" disabled={busy || !name.trim()}>
            {busy ? 'Creating…' : 'Create project'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
