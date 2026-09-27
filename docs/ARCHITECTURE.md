# Architecture

How this is put together, why, and where to extend it. The trade-offs are stated explicitly so
you can overrule them knowingly.

## Package layout

Packages are organised by feature, not by layer. Everything about billing is in `billing/`
rather than smeared across `controllers/`, `services/` and `repositories/`. Deleting a feature
means deleting a directory.

```
com.springlaunch
├── auth/            registration, login, JWT, refresh tokens, the actor model
├── organization/    tenants, memberships, roles
├── billing/         plans, subscriptions, Stripe gateway and webhooks
├── usage/           metering, quota enforcement, the metering interceptor
├── apikey/          machine credentials
├── audit/           append-only activity trail
├── project/         EXAMPLE tenant-scoped resource — replace this
├── common/          base entity, error shape, exception handler
└── config/          security, billing wiring, OpenAPI, MVC
```

## The tenant model

One organization is one tenant. Every business row carries an `organization_id`. There is no
schema-per-tenant and no database-per-tenant, because a single shared schema is dramatically
simpler to migrate and is the right answer until you have a customer who contractually requires
physical separation.

### Isolation is structural, not procedural

The usual way tenant leaks happen is a developer adding `findById` and forgetting the tenant
check. So the repository does not offer that option:

```java
Optional<Project> findByPublicIdAndOrganizationId(String publicId, Long organizationId);
Page<Project> findByOrganizationIdAndArchived(Long organizationId, boolean archived, Pageable p);
```

There is no finder that can be called without a tenant. The organization id comes from the
authenticated actor, never from the request body or a path variable, so a caller has no way to
express "someone else's data". A missing row and another tenant's row both produce the same 404,
which leaks not even the existence of the record.

### Why not Hibernate filters or row-level security?

`@FilterDef` with a `ThreadLocal` tenant works, and it fails silently and dangerously the moment
something runs outside a request — a scheduled job, a queue consumer, a test. Postgres row-level
security is genuinely stronger but ties your authorization model to your database user strategy
and is hard to reason about from Java. Explicit parameters cost one extra argument and are
obvious in a code review. If you later add RLS, this design layers underneath it cleanly.

## Authentication

Two credential types collapse into one `AuthenticatedActor` record, so every service downstream
is written once:

| | Bearer JWT | API key |
|---|---|---|
| Header | `Authorization: Bearer …` | `X-API-Key: sl_live_…` |
| Identifies | a user in an organization | an organization only |
| Effective role | the user's real role | `ADMIN` — never OWNER |
| Storage | stateless, signed | SHA-256 hash of the key |

An API key deliberately cannot reach owner-level actions: it must not be able to commit its
tenant to a recurring charge or mint further credentials. `ApiKeyAuthIntegrationTest` asserts
both refusals.

### Tokens

The access token carries the user id, organization id and role, so authorizing a request needs
no database round trip. The cost is that a role change takes up to one access-token lifetime
(15 minutes) to take effect. That's the trade: fast requests, slightly stale authorization.
Shorten the TTL if that matters more to you than the read load.

Refresh tokens are opaque 256-bit random strings, stored **hashed**, and **rotated on every
use** — so a token works exactly once and a leaked one has a short, detectable life.

### Why SHA-256 for tokens but bcrypt for passwords

Passwords are low-entropy and human-chosen, so they need a deliberately slow hash. Refresh
tokens and API keys are 256 bits of `SecureRandom`; there is no dictionary to attack, so a slow
hash buys nothing — and would cost real latency, since an API key is hashed on *every request*.

## Billing

**Webhooks are the source of truth, not the checkout redirect.** A browser redirect can be
faked, abandoned or lost on a flaky connection. The signed webhook is retried until acknowledged.

```
Customer                  Your API                     Stripe
   │  POST /billing/checkout  │                            │
   │─────────────────────────>│  create checkout session   │
   │                          │───────────────────────────>│
   │  <── hosted checkout URL ─┤                           │
   │──────────────── pays on Stripe's page ───────────────>│
   │                          │  POST /billing/webhook     │
   │                          │<───────────────────────────│
   │                          │  verify signature          │
   │                          │  record event id (once)    │
   │                          │  update plan + org         │
```

Three defences on that endpoint, each with a test:

1. **HMAC-SHA256 signature**, compared in constant time via `MessageDigest.isEqual`.
2. **Timestamp tolerance** of five minutes, so a captured request stops working.
3. **Idempotency** — the event id is inserted under a unique constraint, so a redelivery
   short-circuits instead of applying twice.

The raw request body is read as a `String`, because the signature covers the exact bytes Stripe
sent; deserializing and re-serializing would change them and break verification.

### No Stripe SDK

`BillingGateway` has two methods. `StripeBillingGateway` implements them with `RestClient` over
Stripe's form-encoded API. That's one fewer transitive dependency to keep patched, and it means
`FakeBillingGateway` lets the entire application and test suite run with no Stripe account and no
network. To use the official library instead, replace one class.

### Plans

`Plan` is an enum holding both price and entitlements. Changing a limit is a one-line edit that
the pricing page (`GET /api/v1/plans`) and the enforcement code both follow, so they cannot drift
apart. Stripe owns the prices; this owns what a price *entitles you to*.

`Organization.plan` is denormalized from the subscription so entitlement checks on the hot path
never join or call Stripe. The webhook handler is its only writer.

## Usage metering

`UsageRecord` is one row per (organization, metric, month). Increments are a single atomic
`UPDATE … SET quantity = quantity + n`, which is concurrency-safe without locking and cheap
enough to sit on the request path. The row is created only on the first call of a period.

Metering runs as a `HandlerInterceptor` rather than a filter, because the security context must
already be populated for the tenant to be known. Auth, billing and usage routes are excluded: a
customer at their cap must still be able to log in, see why, and upgrade.

**The honest limit:** this is one database write per request. That is fine into the high hundreds
of requests per second and is the right starting point. Past that, put a Redis counter or an
in-memory buffer in front of `UsageService.increment` and flush periodically — the interface
doesn't change.

## Errors

Every endpoint returns one shape, so clients branch on a stable `code` instead of parsing prose:

```json
{ "code": "quota_exceeded", "message": "…", "timestamp": "…", "details": { … } }
```

| Code | Status | Meaning |
|---|---|---|
| `validation_failed` | 400 | `fieldErrors` names each offending field |
| `unauthorized` | 401 | Missing or bad credentials |
| `forbidden` | 403 | Authenticated but not permitted |
| `not_found` | 404 | Absent — or another tenant's, indistinguishably |
| `conflict` | 409 | Uniqueness violated |
| `quota_exceeded` | **402** | Plan limit reached; `details` drives the upgrade prompt |

402 rather than 403 is deliberate: "pay us" is a different situation from "you may not", and the
frontend should respond differently.

## Database

Flyway migrations in `src/main/resources/db/migration`, written to run identically on PostgreSQL
and on **H2 in PostgreSQL mode** — which means the test suite exercises the same migrations
production runs, so a broken migration fails CI instead of a deploy. `ddl-auto: validate` makes
Hibernate refuse to start if an entity and the schema disagree.

Ids are internal `BIGINT` primary keys plus an external `public_id` UUID. APIs expose only the
UUID, so record counts aren't inferable and ids aren't enumerable.

## Extending it

### Add a tenant-scoped resource

Copy `project/`. The pattern: an `organization_id` column, repository finders that all take the
organization id, the id taken from `AuthenticatedActor`, an entitlement check before the write,
and an audit entry after it.

### Add a metered feature

1. Add a constant to `UsageMetric`.
2. Add a limit column to `Plan`.
3. Add an `assertCanX` method to `EntitlementService`.
4. Call it before the write.

### Add email invitations

There is no mail transport here on purpose — a half-configured one is worse than none. To add
invitations: a table of `(organization_id, email, role, token_hash, expires_at)`, a mail send, and
an accept endpoint that calls `OrganizationService.addMember`. The seat entitlement check is
already in place.

### Add email verification and password reset

`User.emailVerified` exists and is unused. Both flows follow the same shape as refresh tokens:
store a hash of a random token with an expiry, mail the plaintext, verify and consume.

### Before you go live

- [ ] `JWT_SECRET` from `openssl rand -base64 48`, never the shipped default
- [ ] `BILLING_PROVIDER=stripe` with all four Stripe values set
- [ ] Postgres, not H2
- [ ] CORS restricted to your own origins (currently `Customizer.withDefaults()`)
- [ ] Rate limiting in front of `/api/v1/auth/**`
- [ ] A scheduled job calling `RefreshTokenRepository.deleteExpiredBefore`

See [DEPLOY.md](DEPLOY.md).
