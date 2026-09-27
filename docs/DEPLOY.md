# Deploying

## 1. Secrets

```bash
openssl rand -base64 48   # → JWT_SECRET
```

Rotating `JWT_SECRET` invalidates every access token immediately; refresh tokens survive, since
they're database rows rather than signed claims.

| Variable | Required | Notes |
|---|---|---|
| `JWT_SECRET` | **yes** | ≥32 bytes. The app refuses to start otherwise. |
| `DATABASE_URL` | yes | `jdbc:postgresql://host:5432/db` |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | yes | |
| `BILLING_PROVIDER` | yes | `stripe` in production; `fake` charges nobody |
| `STRIPE_SECRET_KEY` | for billing | `sk_live_…` |
| `STRIPE_WEBHOOK_SECRET` | for billing | `whsec_…` |
| `STRIPE_PRICE_PRO` / `STRIPE_PRICE_SCALE` | for billing | `price_…`, not `prod_…` |
| `BILLING_SUCCESS_URL` / `BILLING_CANCEL_URL` | for billing | Where Stripe returns the customer |
| `PORT` | no | Defaults to 8080 |

The app starts with `BILLING_PROVIDER=fake` and logs a warning. That is intentional: you can ship
and onboard users before you've finished Stripe onboarding.

## 2. Database

Any managed Postgres works — RDS, Cloud SQL, Neon, Supabase, Railway. Flyway migrates on startup.

Point `DATABASE_URL` at the new database and start the app; the schema is created from scratch.
For an existing database, `spring.flyway.baseline-on-migrate` is already enabled.

**Zero-downtime migrations:** keep every migration backwards-compatible with the currently
running version — add columns as nullable, backfill, then make them non-null in a later release.
Never rename or drop a column in the same deploy that stops using it.

## 3. Stripe

1. **Create the products.** Stripe Dashboard → Products. One recurring monthly price per paid
   plan. Copy each **price** id (`price_…`, not the product id) into `STRIPE_PRICE_PRO` /
   `STRIPE_PRICE_SCALE`.

2. **Keep the numbers in step.** `Plan` in the code is the source of truth for entitlements;
   Stripe is the source of truth for money. If you change a price in Stripe, change
   `monthlyPriceCents` too — `GET /api/v1/plans` feeds your pricing page from the enum.

3. **Register the webhook.** Developers → Webhooks → add endpoint:

   ```
   https://your-domain.com/api/v1/billing/webhook
   ```

   Subscribe to exactly these events:

   - `checkout.session.completed`
   - `customer.subscription.created`
   - `customer.subscription.updated`
   - `customer.subscription.deleted`
   - `invoice.payment_failed`

   Copy the signing secret into `STRIPE_WEBHOOK_SECRET`.

4. **Test locally** with the Stripe CLI:

   ```bash
   stripe listen --forward-to localhost:8080/api/v1/billing/webhook
   stripe trigger checkout.session.completed
   ```

   `stripe listen` prints its own `whsec_…`; use that one while forwarding.

5. **Use test mode first.** Card `4242 4242 4242 4242` succeeds; `4000 0000 0000 0341` fails after
   attaching, which exercises the `invoice.payment_failed` path.

### Why a failed payment does not lock the customer out

`PAST_DUE` still grants access. Stripe retries a failed card over several days, and locking out a
paying customer over one declined charge turns a temporary blip into cancelled churn. Adjust
`SubscriptionStatus.grantsAccess()` if your business needs otherwise.

## 4. Deploy

### Docker

```bash
docker build -t springlaunch .
docker run -p 8080:8080 --env-file .env springlaunch
```

The image is a multi-stage build on a JRE-only Alpine base, runs as a non-root user, and sizes
its heap from the container limit via `MaxRAMPercentage` — the absence of which is the usual
cause of surprise OOM kills in containers.

### Fly.io

```bash
fly launch --no-deploy
fly postgres create && fly postgres attach <name>
fly secrets set JWT_SECRET=... STRIPE_SECRET_KEY=... STRIPE_WEBHOOK_SECRET=... BILLING_PROVIDER=stripe
fly deploy
```

### Railway / Render

Both detect the Dockerfile. Add a Postgres instance, set the variables above, deploy. Set the
health check path to `/actuator/health/readiness`.

### Kubernetes

Probes are already exposed:

```yaml
livenessProbe:
  httpGet: { path: /actuator/health/liveness, port: 8080 }
readinessProbe:
  httpGet: { path: /actuator/health/readiness, port: 8080 }
  initialDelaySeconds: 20
```

Run a single replica for the first migration-bearing deploy; Flyway takes a lock, so concurrent
starts are safe but serialize.

## 5. Production checklist

- [ ] `JWT_SECRET` is generated, not the shipped default
- [ ] `BILLING_PROVIDER=stripe` and the startup warning is gone
- [ ] HTTPS terminated in front of the app
- [ ] **CORS narrowed.** `SecurityConfig` ships `Customizer.withDefaults()`; add a
      `CorsConfigurationSource` bean listing your own origins
- [ ] **Rate limiting** on `/api/v1/auth/**` — at your proxy, or with Bucket4j
- [ ] `server.error.include-message: never` is already set; keep it
- [ ] Refresh-token cleanup scheduled (`deleteExpiredBefore`)
- [ ] Database backups and point-in-time recovery enabled
- [ ] Alert on 5xx rate and on webhook handler failures — a silently failing webhook means
      customers who paid and didn't get access

## 6. Operating it

**A customer paid but has no access.** Check Stripe's webhook delivery log first. A 401 there
means `STRIPE_WEBHOOK_SECRET` is wrong; a 5xx means the handler threw. Stripe retries for up to
three days, so fixing the cause and replaying from the dashboard recovers it without manual
database edits.

**Events arriving twice.** Expected, and handled — `processed_webhook_events` absorbs them.

**Reconciling manually.** `subscriptions.stripe_subscription_id` is unique, so a subscription maps
to exactly one tenant and vice versa.
