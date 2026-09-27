import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';
import type { ApiKey, CreatedApiKey } from '../api/types';
import { PageHeader } from '../components/Layout';
import { Badge, Card, Empty, ErrorNotice, Modal, formatDate } from '../components/ui';

export function ApiKeys() {
  const [keys, setKeys] = useState<ApiKey[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [creating, setCreating] = useState(false);
  const [issued, setIssued] = useState<CreatedApiKey | null>(null);

  const load = useCallback(async () => {
    try {
      setKeys(await api.get<ApiKey[]>('/api-keys'));
    } catch (caught) {
      setError(caught);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <>
      <PageHeader title="API keys">
        <button type="button" className="btn" onClick={() => setCreating(true)}>
          Create key
        </button>
      </PageHeader>

      <div className="content">
        <ErrorNotice error={error} />

        <Card flush>
          <div className="table-wrap">
            {!keys ? (
              <p className="skeleton" style={{ paddingLeft: 20 }}>
                Loading…
              </p>
            ) : keys.length === 0 ? (
              <Empty>No API keys yet. Create one to call the API from your own services.</Empty>
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Key</th>
                    <th>Last used</th>
                    <th>Status</th>
                    <th className="right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {keys.map((key) => (
                    <tr key={key.id}>
                      <td>
                        <strong style={{ fontWeight: 600 }}>{key.name}</strong>
                      </td>
                      <td className="mono" style={{ color: 'var(--ink-soft)' }}>{key.keyPrefix}…</td>
                      <td>{key.lastUsedAt ? formatDate(key.lastUsedAt) : 'Never'}</td>
                      <td>
                        {key.revokedAt ? (
                          <Badge tone="danger">Revoked</Badge>
                        ) : (
                          <Badge tone="leaf">Active</Badge>
                        )}
                      </td>
                      <td className="right">
                        {!key.revokedAt && (
                          <button
                            type="button"
                            className="btn sm danger"
                            onClick={async () => {
                              setError(null);
                              try {
                                await api.del(`/api-keys/${key.id}`);
                                await load();
                              } catch (caught) {
                                setError(caught);
                              }
                            }}
                          >
                            Revoke
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </Card>

        <p className="card-sub">
          Send a key as the <code>X-API-Key</code> header. Keys act with ADMIN rights and can never
          start a subscription or create further keys.
        </p>
      </div>

      {creating && (
        <CreateKey
          onClose={() => setCreating(false)}
          onCreated={(key) => {
            setCreating(false);
            setIssued(key);
            void load();
          }}
        />
      )}

      {issued && <ShowKeyOnce issued={issued} onClose={() => setIssued(null)} />}
    </>
  );
}

function CreateKey({
  onClose,
  onCreated,
}: {
  onClose: () => void;
  onCreated: (key: CreatedApiKey) => void;
}) {
  const [name, setName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      onCreated(await api.post<CreatedApiKey>('/api-keys', { name }));
    } catch (caught) {
      setError(caught);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title="Create an API key" onClose={onClose}>
      <ErrorNotice error={error} />
      <form className="stack" onSubmit={submit}>
        <div className="field">
          <label htmlFor="key-name">Name</label>
          <input
            id="key-name"
            required
            autoFocus
            placeholder="ci-pipeline"
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
          <p className="hint">Name it after where it will be used, so you know what to revoke later.</p>
        </div>
        <div className="modal-actions">
          <button type="button" className="btn secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn" disabled={busy || !name.trim()}>
            {busy ? 'Creating…' : 'Create key'}
          </button>
        </div>
      </form>
    </Modal>
  );
}

/** The plaintext exists only in the create response, so this is the one chance to copy it. */
function ShowKeyOnce({ issued, onClose }: { issued: CreatedApiKey; onClose: () => void }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(issued.key);
      setCopied(true);
    } catch {
      // Clipboard refused: the key is on screen and selectable, which is the fallback.
      setCopied(false);
    }
  }

  return (
    <Modal title="Copy your key now" onClose={onClose}>
      <div className="notice quota">
        <p>
          <strong>This is the only time this key is shown.</strong> Only a hash of it is stored, so if
          you lose it you will have to create a new one.
        </p>
      </div>
      <p className="secret">{issued.key}</p>
      <div className="modal-actions">
        <button type="button" className="btn secondary" onClick={() => void copy()}>
          {copied ? 'Copied' : 'Copy'}
        </button>
        <button type="button" className="btn" onClick={onClose}>
          Done
        </button>
      </div>
    </Modal>
  );
}
