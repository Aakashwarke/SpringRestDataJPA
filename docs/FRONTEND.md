# Frontend

An optional React dashboard that exercises every endpoint the API exposes.

**It is a separate module, and deleting it costs you nothing.** The default Maven build never
touches it, so `mvn spring-boot:run` and `mvn test` stay pure Java with no Node download. If you
are bringing your own UI, delete `frontend/`, `SpaForwardingConfig` and the `frontend` profile
from `pom.xml`, and nothing else changes.

## Running it

**In development** — two terminals, with hot reload:

```bash
mvn spring-boot:run            # API on :8080
cd frontend && npm install && npm run dev   # UI on :5173
```

Vite proxies `/api` to port 8080, so the browser sees a single origin and there is no CORS
configuration to get wrong while developing.

**As one deployable artifact:**

```bash
mvn -Pfrontend clean package
java -jar target/springlaunch-starter-1.0.0.jar    # API and UI together on :8080
```

The profile downloads a pinned Node, runs `npm ci` against the committed lockfile, builds, and
copies `dist/` into `classpath:/static`.

## What's in it

| Screen | Covers |
|---|---|
| Sign in / Register | Registration provisions user, org and subscription in one call |
| Overview | Usage meters against plan limits, past-due banner, recent projects |
| Projects | Full CRUD, archive/restore, and the 402 upgrade prompt |
| Members | Roles, seat limits, last-owner protection |
| API keys | Issue, copy-once, revoke |
| Billing | Live plan catalog, checkout, billing portal |
| Audit log | The tenant's activity trail |

## Decisions worth knowing

**Plain CSS with custom properties, not Tailwind.** One stylesheet (`src/styles.css`) holds every
token. Restyling to your brand means editing the `:root` block, there is no PostCSS or config to
migrate on a major version, and the CSS ships at ~2.6 kB gzipped.

**No component library.** The shared primitives in `components/ui.tsx` are about 200 lines. A kit
that pulled in MUI or shadcn would be handing you someone else's upgrade treadmill.

**The access token lives in memory; only the refresh token is persisted.**
That keeps the short-lived credential out of storage entirely. The refresh token does have to
survive a reload, so it sits in `localStorage` — which is readable by any script on the page.
This is a deliberate, documented trade-off, not an oversight. The stronger option is an httpOnly,
`SameSite=Strict` cookie issued by the backend, which needs a cookie-based auth endpoint and CSRF
protection; if your threat model includes untrusted third-party scripts, make that change before
launch.

**One shared refresh promise.** Refresh tokens are single-use, so two requests refreshing at once
would race and one would lose its token. `api/client.ts` shares a single in-flight refresh, so a
burst of 401s triggers exactly one exchange.

**The UI hides what the API would refuse.** `atLeast(role, 'ADMIN')` mirrors the backend's ranked
roles. That is a usability measure only — every check is still enforced server-side, and the UI
never decides authorization.

**Errors render from the API's own shape.** A 402 becomes an upgrade prompt built from the
`details` the backend sends (`limit`, `current`, `plan`), so the numbers on screen can never drift
from the numbers enforced.

## Verification

The dashboard has no automated test suite; it was verified by a scripted browser walkthrough
against the packaged jar, which is shipped as `frontend/e2e/walkthrough.mjs` so you can re-run it:

```bash
cd frontend
npm install --no-save playwright && npx playwright install chromium
BASE_URL=http://localhost:8080 node e2e/walkthrough.mjs
```

It registers a tenant, fills the free plan to its limit, asserts the 402 prompt shows the real
numbers, issues and revokes an API key, checks the plaintext never reappears, exercises both
themes, confirms a hard refresh restores the session, and checks for horizontal overflow at
390px — writing a screenshot per step as it goes.

Playwright is intentionally not a dependency: it would add a browser download to every
`npm install` for a check most buyers run once.

## Fonts

Type is loaded from Google Fonts with full fallback stacks. On a restricted network the fonts
fail to load and the UI renders in system faces, which is a visual change only. To remove the
external request entirely, self-host the woff2 files and replace the `<link>` in `index.html`.
