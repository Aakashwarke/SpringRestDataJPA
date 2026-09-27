import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { Member, OrgRole } from '../api/types';
import { atLeast, useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/Layout';
import { Badge, Card, Empty, ErrorNotice, Modal, formatDate } from '../components/ui';

const ROLES: OrgRole[] = ['MEMBER', 'ADMIN', 'OWNER'];

export function Members() {
  const { role, user } = useAuth();
  const navigate = useNavigate();
  const [members, setMembers] = useState<Member[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [adding, setAdding] = useState(false);

  const load = useCallback(async () => {
    try {
      setMembers(await api.get<Member[]>('/organization/members'));
    } catch (caught) {
      setError(caught);
    }
  }, []);

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

  const canManage = atLeast(role, 'ADMIN');

  return (
    <>
      <PageHeader title="Members">
        {canManage && (
          <button type="button" className="btn" onClick={() => setAdding(true)}>
            Add member
          </button>
        )}
      </PageHeader>

      <div className="content">
        <ErrorNotice error={error} onUpgrade={() => navigate('/billing')} />

        <Card flush>
          <div className="table-wrap">
            {!members ? (
              <p className="skeleton" style={{ paddingLeft: 20 }}>
                Loading…
              </p>
            ) : members.length === 0 ? (
              <Empty>No members.</Empty>
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Email</th>
                    <th>Role</th>
                    <th>Joined</th>
                    {canManage && <th className="right">Actions</th>}
                  </tr>
                </thead>
                <tbody>
                  {members.map((member) => {
                    const isSelf = member.userId === user?.user.id;
                    return (
                      <tr key={member.userId}>
                        <td>
                          <strong style={{ fontWeight: 600 }}>{member.fullName}</strong>
                          {isSelf && (
                            <>
                              {' '}
                              <Badge>You</Badge>
                            </>
                          )}
                        </td>
                        <td style={{ color: 'var(--ink-soft)' }}>{member.email}</td>
                        <td>
                          {canManage && !isSelf ? (
                            <select
                              aria-label={`Role for ${member.fullName}`}
                              value={member.role}
                              onChange={(event) =>
                                void act(() =>
                                  api.put(`/organization/members/${member.userId}/role`, {
                                    role: event.target.value,
                                  }),
                                )
                              }
                              style={{ font: 'inherit', padding: '3px 6px' }}
                            >
                              {ROLES.filter((r) => r !== 'OWNER' || atLeast(role, 'OWNER')).map((r) => (
                                <option key={r} value={r}>
                                  {r}
                                </option>
                              ))}
                            </select>
                          ) : (
                            <Badge tone={member.role === 'OWNER' ? 'leaf' : undefined}>{member.role}</Badge>
                          )}
                        </td>
                        <td>{formatDate(member.joinedAt)}</td>
                        {canManage && (
                          <td className="right">
                            {!isSelf && (
                              <button
                                type="button"
                                className="btn sm danger"
                                onClick={() =>
                                  void act(() => api.del(`/organization/members/${member.userId}`))
                                }
                              >
                                Remove
                              </button>
                            )}
                          </td>
                        )}
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </div>
        </Card>

        <p className="card-sub">
          An organization always keeps at least one owner, so the last one cannot be removed or demoted.
        </p>
      </div>

      {adding && (
        <AddMember
          onClose={() => setAdding(false)}
          onAdded={() => {
            setAdding(false);
            void load();
          }}
          onError={(caught) => {
            setError(caught);
            setAdding(false);
          }}
          canGrantOwner={atLeast(role, 'OWNER')}
        />
      )}
    </>
  );
}

function AddMember({
  onClose,
  onAdded,
  onError,
  canGrantOwner,
}: {
  onClose: () => void;
  onAdded: () => void;
  onError: (error: unknown) => void;
  canGrantOwner: boolean;
}) {
  const [email, setEmail] = useState('');
  const [memberRole, setMemberRole] = useState<OrgRole>('MEMBER');
  const [busy, setBusy] = useState(false);
  const [localError, setLocalError] = useState<unknown>(null);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setLocalError(null);
    try {
      await api.post('/organization/members', { email, role: memberRole });
      onAdded();
    } catch (caught) {
      if (caught && typeof caught === 'object' && 'isQuota' in caught && caught.isQuota) onError(caught);
      else setLocalError(caught);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title="Add a member" onClose={onClose}>
      <ErrorNotice error={localError} />
      <p className="card-sub">
        The person needs an account already. This kit ships no mail transport, so there is no
        email-invitation flow to half-configure — see docs/ARCHITECTURE.md to add one.
      </p>
      <form className="stack" onSubmit={submit}>
        <div className="field">
          <label htmlFor="member-email">Their email</label>
          <input
            id="member-email"
            type="email"
            required
            autoFocus
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="member-role">Role</label>
          <select
            id="member-role"
            value={memberRole}
            onChange={(event) => setMemberRole(event.target.value as OrgRole)}
          >
            {ROLES.filter((r) => r !== 'OWNER' || canGrantOwner).map((r) => (
              <option key={r} value={r}>
                {r}
              </option>
            ))}
          </select>
        </div>
        <div className="modal-actions">
          <button type="button" className="btn secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn" disabled={busy || !email.trim()}>
            {busy ? 'Adding…' : 'Add member'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
