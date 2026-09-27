# SpringLaunch — Spring Boot SaaS Starter Kit

**Stop rebuilding auth, tenancy and Stripe billing. Start on the part that's actually your product.**

A production-shaped multi-tenant SaaS backend in Spring Boot 3.3 and Java 21. Every piece of
plumbing a subscription business needs on day one is here, wired together and covered by tests.

```bash
git clone <your-repo> && cd springlaunch
mvn spring-boot:run
# → http://localhost:8080/swagger-ui.html
```

No database to install, no Stripe account, no API keys. It boots.

---

## What's in the box

| | |
|---|---|
| **Multi-tenancy** | Organizations, memberships, ranked roles (OWNER / ADMIN / MEMBER), tenant switching |
| **Authentication** | Registration, login, JWT access tokens, rotating refresh tokens, bcrypt, revocation |
| **API keys** | Hashed machine credentials, `sl_live_…` prefix, instant revocation, per-tenant scope |
| **Stripe billing** | Checkout, billing portal, signature-verified webhooks, idempotent event handling |
| **Plans & entitlements** | One enum defines pricing and limits; the backend enforces exactly what the pricing page shows |
| **Usage metering** | Per-tenant, per-month counters with atomic increments and 402 quota enforcement |
| **Audit log** | Append-only, per-tenant trail of who did what |
| **Operations** | Flyway migrations, OpenAPI/Swagger, health probes, Docker, docker-compose, GitHub Actions CI |
| **Tests** | 40 tests: tenant isolation, quota boundaries, token rotation, webhook forgery and replay |

## The three things that are hard to get right, and are done here

**1. Tenant isolation that doesn't depend on remembering a check.**
`ProjectRepository` has no finder that can be called without an organization id. An endpoint
that *cannot* ask for another tenant's row cannot leak one. `TenantIsolationIntegrationTest`
proves that a fully authenticated customer who knows the exact id of another customer's record
gets an indistinguishable 404 on every verb — read, update, delete and archive.

**2. A webhook endpoint that can't be forged or replayed.**
The Stripe webhook is public by necessity, so its HMAC signature is the only thing between an
attacker and a free "you are now on the Scale plan" event. `StripeSignatureVerifier` does a
constant-time comparison and rejects stale timestamps, and the test suite attacks it: tampered
payloads, a signature from the attacker's own secret, and a genuine request replayed ten minutes
later. Delivery is at-least-once, so events are recorded under a unique constraint and applied
exactly once.

**3. Plan limits that hold exactly at the boundary.**
One project too few and you annoy paying customers; one too many and you give the product away.
`PlanQuotaIntegrationTest` asserts the boundary itself, and the billing test walks the whole
revenue path: hit the free wall, receive a signed upgrade webhook, watch the same request
succeed.

## Documentation

| Document | What it covers |
|---|---|
| [docs/QUICKSTART.md](docs/QUICKSTART.md) | Running it, and your first authenticated request |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | How it's built, the trade-offs taken, and where to extend |
| [docs/DEPLOY.md](docs/DEPLOY.md) | Postgres, secrets, Stripe setup, and going live |
| [LICENSE.md](LICENSE.md) | Commercial license terms |

## Stack

Java 21 · Spring Boot 3.3.5 · Spring Data JPA · Spring Security · Flyway · PostgreSQL (H2 for
dev and tests) · JJWT · springdoc-openapi · Lombok.

Deliberately **not** included: no Stripe SDK (two REST calls behind a one-method interface), no
MapStruct, no Kafka, no service mesh. Every dependency here is one you'd have added anyway.

## Verify it yourself

```bash
mvn clean test     # 40 tests, no network or database required
```

```
Tests run: 40, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Where to start reading

1. `ProjectService` — the tenant-scoped service pattern to copy for your own domain.
2. `Plan` — pricing and limits in one enum; change a number, and the whole product follows.
3. `StripeWebhookService` — how money becomes entitlements.
4. `SecurityConfig` — the whole authentication chain on one screen.

Then delete `project/` and model your own domain the same way.
