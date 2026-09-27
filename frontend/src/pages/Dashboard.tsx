import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { Page, Project, SubscriptionSummary, UsageResponse } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/Layout';
import { Badge, Card, Empty, ErrorNotice, Stat, formatCount, formatDate } from '../components/ui';

export function Dashboard() {
  const { user } = useAuth();
  const [usage, setUsage] = useState<UsageResponse | null>(null);
  const [subscription, setSubscription] = useState<SubscriptionSummary | null>(null);
  const [projects, setProjects] = useState<Page<Project> | null>(null);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    (async () => {
      try {
        // Independent reads, so fetch them together rather than in sequence.
        const [usageData, subscriptionData, projectData] = await Promise.all([
          api.get<UsageResponse>('/usage'),
          api.get<SubscriptionSummary>('/billing/subscription'),
          api.get<Page<Project>>('/projects?size=5'),
        ]);
        setUsage(usageData);
        setSubscription(subscriptionData);
        setProjects(projectData);
      } catch (caught) {
        setError(caught);
      }
    })();
  }, []);

  const apiCalls = usage?.metrics.find((metric) => metric.metric === 'api_calls');
  const entitlements = subscription?.entitlements;
  const projectCount = projects?.totalItems ?? 0;

  return (
    <>
      <PageHeader title={`Welcome back, ${user?.user.fullName.split(' ')[0] ?? ''}`}>
        {subscription && (
          <Badge tone={subscription.status === 'PAST_DUE' ? 'signal' : 'leaf'}>
            {subscription.plan} · {subscription.status}
          </Badge>
        )}
      </PageHeader>

      <div className="content">
        <ErrorNotice error={error} />

        {subscription?.status === 'PAST_DUE' && (
          <div className="notice quota">
            <p>
              <strong>Your last payment failed.</strong> Your access continues while we retry, but
              please update your card to avoid interruption.
            </p>
            <div>
              <Link className="btn sm" to="/billing">
                Update payment
              </Link>
            </div>
          </div>
        )}

        <div className="grid cols-3">
          <Stat
            label="Active projects"
            value={String(projectCount)}
            sub={entitlements ? `of ${formatCount(entitlements.maxProjects)} on ${subscription?.plan}` : undefined}
            meter={entitlements ? { used: projectCount, limit: entitlements.maxProjects } : undefined}
          />
          <Stat
            label={`API calls · ${usage?.period ?? ''}`}
            value={apiCalls ? apiCalls.used.toLocaleString() : '—'}
            sub={apiCalls ? `of ${formatCount(apiCalls.limit)} this month` : undefined}
            meter={apiCalls ? { used: apiCalls.used, limit: apiCalls.limit } : undefined}
          />
          <Stat
            label="Seats used"
            value={String(user?.organizations.length ? subscription?.seats ?? 1 : 1)}
            sub={entitlements ? `of ${formatCount(entitlements.maxSeats)} available` : undefined}
          />
        </div>

        <Card
          title="Recent projects"
          flush
          actions={
            <Link className="btn sm secondary" to="/projects">
              View all
            </Link>
          }
        >
          <div className="table-wrap">
            {!projects ? (
              <p className="skeleton" style={{ paddingLeft: 20 }}>
                Loading…
              </p>
            ) : projects.items.length === 0 ? (
              <Empty>
                No projects yet. <Link to="/projects">Create your first one.</Link>
              </Empty>
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Status</th>
                    <th>Created</th>
                  </tr>
                </thead>
                <tbody>
                  {projects.items.map((project) => (
                    <tr key={project.id}>
                      <td>{project.name}</td>
                      <td>
                        {project.archived ? <Badge>Archived</Badge> : <Badge tone="leaf">Active</Badge>}
                      </td>
                      <td>{formatDate(project.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </Card>
      </div>
    </>
  );
}
