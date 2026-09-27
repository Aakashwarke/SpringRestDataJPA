# Quickstart

Java 21 and Maven are the only prerequisites. No database, no Stripe account.

## 1. Run it

```bash
mvn spring-boot:run
```

Flyway creates the schema in an in-memory H2 database, so the first run needs no setup. Open
<http://localhost:8080/swagger-ui.html> for the full interactive API.

## 2. Create an account

Registration provisions the user, their first organization, an OWNER membership and a free
subscription in one transaction.

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "founder@acme.test",
    "password": "correct-horse-battery",
    "fullName": "Ada Founder",
    "organizationName": "Acme Rockets"
  }'
```

```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "x7Kp...",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "organizationId": "30a0601c-7d86-46e1-8445-e2ac80b6727b",
  "role": "OWNER"
}
```

## 3. Use it

```bash
TOKEN=<accessToken from above>

curl -X POST http://localhost:8080/api/v1/projects \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name": "First Rocket"}'
```

## 4. Watch a plan limit fire

The free plan allows three projects. Create a fourth:

```json
{
  "code": "quota_exceeded",
  "message": "Plan limit reached for projects. Upgrade to raise this limit.",
  "details": { "metric": "projects", "limit": 3, "current": 3, "plan": "FREE" }
}
```

HTTP 402 with the numbers your upgrade prompt needs. Every limit in the product answers in this
shape, so the frontend handles them all once.

## 5. Issue a machine credential

```bash
curl -X POST http://localhost:8080/api/v1/api-keys \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name": "ci-pipeline"}'
```

The plaintext `key` is in this response and nowhere else — only its hash is stored. Use it in
place of a bearer token:

```bash
curl http://localhost:8080/api/v1/projects -H "X-API-Key: sl_live_..."
```

## 6. Check usage

```bash
curl http://localhost:8080/api/v1/usage -H "Authorization: Bearer $TOKEN"
```

```json
{
  "period": "2026-09",
  "plan": "FREE",
  "metrics": [{ "metric": "api_calls", "used": 6, "limit": 1000, "percentUsed": 0 }]
}
```

This endpoint is never metered itself, so a customer who has hit their cap can still see why.

## Endpoint map

| Method | Path | Notes |
|---|---|---|
| POST | `/api/v1/auth/register` | Public |
| POST | `/api/v1/auth/login` | Public |
| POST | `/api/v1/auth/refresh` | Public; rotates the refresh token |
| POST | `/api/v1/auth/logout` | Revokes every session |
| GET | `/api/v1/auth/me` | User plus all organizations |
| POST | `/api/v1/auth/switch-organization` | Re-scopes the access token |
| GET | `/api/v1/plans` | Public; drives the pricing page |
| GET | `/api/v1/organization` | Active tenant |
| PUT | `/api/v1/organization` | ADMIN |
| GET/POST | `/api/v1/organization/members` | ADMIN to add; 402 when out of seats |
| PUT/DELETE | `/api/v1/organization/members/{userId}` | ADMIN; last owner is protected |
| GET | `/api/v1/billing/subscription` | Plan, status, period end |
| POST | `/api/v1/billing/checkout` | OWNER only |
| POST | `/api/v1/billing/portal` | OWNER only |
| POST | `/api/v1/billing/webhook` | Public; authenticated by HMAC signature |
| CRUD | `/api/v1/projects` | Example tenant-scoped resource |
| GET/POST/DELETE | `/api/v1/api-keys` | ADMIN |
| GET | `/api/v1/usage` | Never metered |
| GET | `/api/v1/audit-logs` | ADMIN |

## Running against Postgres

```bash
docker compose up --build
```

## Next

Read [ARCHITECTURE.md](ARCHITECTURE.md), then replace `project/` with your own domain.
