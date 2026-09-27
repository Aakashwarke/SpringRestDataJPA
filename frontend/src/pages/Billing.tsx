import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { PlanDescriptor, SubscriptionSummary } from '../api/types';
import { atLeast, useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/Layout';
import {
  Badge,
  Card,
  ErrorNotice,
  formatCount,
  formatDate,
  formatPrice,
} from '../components/ui';

export function Billing() {
  const { role } = useAuth();
  const [plans, setPlans] = useState<PlanDescriptor[] | null>(null);
  const [subscription, setSubscription] = useState<SubscriptionSummary | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [busyPlan, setBusyPlan] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const [planData, subscriptionData] = await Promise.all([
          api.get<PlanDescriptor[]>('/plans'),
          api.get<SubscriptionSummary>('/billing/subscription'),
        ]);
        setPlans(planData);
        setSubscription(subscriptionData);
      } catch (caught) {
        setError(caught);
      }
    })();
  }, []);

  const isOwner = atLeast(role, 'OWNER');

  async function startCheckout(plan: string) {
    setError(null);
    setBusyPlan(plan);
    try {
      const { checkoutUrl } = await api.post<{ checkoutUrl: string }>('/billing/checkout', { plan });
      // The backend returns the payment provider's hosted page; hand the browser over to it.
      window.location.href = checkoutUrl;
    } catch (caught) {
      setError(caught);
    } finally {
      setBusyPlan(null);
    }
  }

  async function openPortal() {
    setError(null);
    try {
      const { portalUrl } = await api.post<{ portalUrl: string }>('/billing/portal');
      window.location.href = portalUrl;
    } catch (caught) {
      setError(caught);
    }
  }

  return (
    <>
      <PageHeader title="Billing">
        {subscription && (
          <Badge tone={subscription.status === 'PAST_DUE' ? 'signal' : 'leaf'}>
            {subscription.status}
          </Badge>
        )}
      </PageHeader>

      <div className="content">
        <ErrorNotice error={error} />

        {!isOwner && (
          <div className="notice">
            <p>Only an organization owner can change the plan. You can see it here.</p>
          </div>
        )}

        {subscription && (
          <Card
            title={`Current plan: ${subscription.plan}`}
            subtitle={
              subscription.currentPeriodEnd
                ? `${subscription.cancelAtPeriodEnd ? 'Ends' : 'Renews'} ${formatDate(subscription.currentPeriodEnd)}`
                : 'No renewal date — free plans do not renew.'
            }
            actions={
              isOwner && subscription.plan !== 'FREE' ? (
                <button type="button" className="btn secondary" onClick={() => void openPortal()}>
                  Manage subscription
                </button>
              ) : undefined
            }
          >
            <div className="grid cols-3">
              <div>
                <p className="stat k">Projects</p>
                <p className="num" style={{ fontFamily: 'var(--mono)' }}>
                  {formatCount(subscription.entitlements.maxProjects)}
                </p>
              </div>
              <div>
                <p className="stat k">Seats</p>
                <p className="num" style={{ fontFamily: 'var(--mono)' }}>
                  {formatCount(subscription.entitlements.maxSeats)}
                </p>
              </div>
              <div>
                <p className="stat k">API calls / month</p>
                <p className="num" style={{ fontFamily: 'var(--mono)' }}>
                  {formatCount(subscription.entitlements.monthlyApiCalls)}
                </p>
              </div>
            </div>
          </Card>
        )}

        <div>
          <div className="card-head">
            <div>
              <h2 style={{ fontFamily: 'var(--serif)', fontSize: '1.02rem', fontWeight: 600 }}>
                Plans
              </h2>
              <p className="card-sub">
                These limits come from <code>GET /api/v1/plans</code> — the same catalog the backend
                enforces, so this page can never show a limit the API disagrees with.
              </p>
            </div>
          </div>

          <div className="grid cols-3">
            {plans?.map((plan) => {
              const current = subscription?.plan === plan.id;
              return (
                <div key={plan.id} className={current ? 'card plan-tier current' : 'card plan-tier'}>
                  <div className="card-head" style={{ marginBottom: 0 }}>
                    <h3 style={{ fontSize: '1rem', fontWeight: 600 }}>{plan.name}</h3>
                    <div className="spacer" />
                    {current && <Badge tone="leaf">Current</Badge>}
                  </div>
                  <p className="price num">
                    {formatPrice(plan.monthlyPriceCents)}
                    {plan.monthlyPriceCents > 0 && <small> / month</small>}
                  </p>
                  <ul>
                    <li>{formatCount(plan.maxProjects)} projects</li>
                    <li>{formatCount(plan.maxSeats)} seats</li>
                    <li>{formatCount(plan.monthlyApiCalls)} API calls / month</li>
                  </ul>
                  {isOwner && plan.monthlyPriceCents > 0 && !current && (
                    <button
                      type="button"
                      className="btn"
                      disabled={busyPlan !== null}
                      onClick={() => void startCheckout(plan.id)}
                    >
                      {busyPlan === plan.id ? 'Opening…' : `Upgrade to ${plan.name}`}
                    </button>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </>
  );
}
