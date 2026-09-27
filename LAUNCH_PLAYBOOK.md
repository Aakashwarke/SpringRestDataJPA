# Launch Playbook — turning this repo into revenue

The code is built. This is the other half of the job, and honestly the harder half.

Read the **Reality check** at the bottom before you set expectations.

---

## 1. The offer

You are not selling "a Spring Boot project". You are selling **three weeks of the most tedious,
highest-risk work in any SaaS build, already done and tested.**

The one-line pitch:

> **Ship your Spring Boot SaaS this weekend, not next quarter.** Multi-tenancy, JWT auth, Stripe
> subscriptions, usage metering and plan limits — production-ready, fully tested, Java 21.

Why a Spring developer buys it:

- Auth, tenancy and billing take 2–4 weeks to build properly, and their bugs are the expensive
  kind: a tenant leak is a breach, a webhook bug is a customer who paid and got nothing.
- They are the *least* differentiated parts of the product. Nobody has ever chosen a SaaS for its
  refresh-token rotation.
- At a €600/day contractor rate, three weeks saved is a five-figure saving. $99 is not a decision
  that needs a business case.

**The market gap is the whole opportunity.** The JavaScript world has a dozen paid boilerplates
clearing six figures a year. Java and Spring — the language enterprises actually build on, whose
developers have expense accounts — has almost nothing comparable. You are early to an underserved
segment, not late to a crowded one.

## 2. Pricing

| Tier | Price | What it is |
|---|---|---|
| **Solo** | **$99** | One developer, unlimited own projects |
| **Team** | **$249** | Everyone at one company |
| Launch discount | **$69** | First 25 buyers, code `LAUNCH25` |

Reasoning:

- Under $100 is an impulse buy for a professional developer, and needs no approval.
- The comparable JS kits sit at $199–$299. Undercutting them reads as fair for a newer product
  while leaving obvious room to raise prices later.
- **Do not price below $49.** A cheap developer tool reads as an abandoned side project, and you
  will need 10× the buyers for the same money — which means 10× the support.
- Raise to $129 after the first 25 sales. Early buyers got a real discount, later buyers see a
  product with traction. Both true.

One-time purchase, not a subscription. Buyers of source code resent recurring fees for something
they already have on disk, and a subscription obliges you to keep shipping.

## 3. Setup — about 90 minutes

### Where to sell it

| Platform | Fee | Why |
|---|---|---|
| **Lemon Squeezy** | ~5% + 50¢ | **Start here.** Merchant of record: they handle EU VAT, US sales tax and invoices for you. For a solo seller selling internationally, that alone is worth the fee. |
| **Polar** | ~4% | Developer-focused, native GitHub repo access granting. Excellent fit; newer. |
| **Gumroad** | 10% | Simplest, most familiar to buyers, highest fee. |
| **Stripe directly** | ~2.9% | Cheapest, but **you** become responsible for VAT/sales tax registration and remittance in every jurisdiction you sell to. Not worth it at this volume. |

Tax is the reason to use a merchant of record. Selling digital goods to EU consumers creates a VAT
obligation from the first sale, and you do not want that job.

### Delivery

Sell **access to a private GitHub repository**, not a zip file:

1. Push this code to a new **private** repo — `springlaunch` or your own name for it.
2. On purchase, add the buyer as a collaborator (Polar automates this; on Lemon Squeezy use a
   webhook or do it by hand at first — manual is fine for the first 20 sales).
3. Buyers get `git pull` for updates, which is a genuine ongoing benefit and a reason to choose
   you over a zip.

Include in the purchase: the private repo, `LICENSE.md`, and the docs. Promise 12 months of
updates. Do **not** promise support hours you can't deliver — under-promise here.

### The sales page

A landing page is non-negotiable. It needs, in this order:

1. Headline: the outcome, not the technology.
2. A 60-second demo — `mvn spring-boot:run` to a working API, ideally a screen recording.
3. The feature table from `README.md`.
4. **The test output.** `Tests run: 40, Failures: 0` does more for a technical buyer than any
   adjective. This is your single strongest asset — almost no boilerplate ships with proof.
5. The three hard problems (tenant isolation, webhook forgery, quota boundaries) with the code.
6. Pricing, licence terms, refund policy.
7. FAQ: Java version? Postgres? Can I use it for client work? Is it really one-time?

## 4. Launch week

Do **not** launch everywhere on day one. Stagger it, so each channel's feedback improves the next.

### Day 1 — Soft launch
Post to **r/SpringBoot** and **r/java**. Read each subreddit's self-promotion rules first, and
lead with substance rather than a link. A post that teaches something earns the right to mention
a product:

> **Title:** How I handle multi-tenant isolation in Spring Data JPA without Hibernate filters
>
> The usual advice is a `@FilterDef` with a ThreadLocal tenant id. I've been burned by that —
> it fails silently the moment anything runs outside a web request: a scheduled job, a queue
> consumer, a test.
>
> What I do instead is make the unsafe query impossible to write. The repository exposes no
> finder that can be called without a tenant id:
>
> ```java
> Optional<Project> findByPublicIdAndOrganizationId(String publicId, Long organizationId);
> Page<Project> findByOrganizationIdAndArchived(Long orgId, boolean archived, Pageable p);
> ```
>
> There's no `findById` to forget to guard. The org id comes from the authenticated principal,
> never the request, so a caller can't even express "someone else's row". Missing rows and other
> tenants' rows both 404 — you don't leak existence.
>
> One extra parameter, and the failure mode moves from "silent leak in production" to "won't
> compile".
>
> (I packaged this and the rest of the SaaS plumbing — auth, Stripe, metering — as a starter kit;
> link in my profile if that's useful, happy to just talk about the pattern here.)

This is the right shape: genuinely useful standalone, honest about the commercial interest, link
demoted. Answer every comment.

### Day 2–3 — Write the deep dive
A technical article on dev.to, Hashnode or your own blog. Best-performing angle:

> **"Stripe webhooks are the only source of truth: getting SaaS billing right in Spring Boot"**

Cover signature verification, why the redirect can't be trusted, and idempotency under
at-least-once delivery. Include real code. Link the kit once, at the end.

This is the asset that keeps working. Reddit traffic dies in 48 hours; a good article on
"spring boot stripe webhook" ranks for years and sells while you sleep.

### Day 4 — Hacker News
`Show HN: SpringLaunch – Spring Boot SaaS starter kit (multi-tenant, Stripe, metered)`

Post at roughly 8–10am US Eastern on a weekday. Be in the comments all day. HN is blunt about
paid boilerplates — answer criticism straight, never defensively. Expect it to flop; the cost is
one hour, and the upside is a few hundred qualified visitors.

### Day 5 — LinkedIn and X
LinkedIn genuinely works for enterprise-Java audiences. Lead with the problem:

> Every SaaS I've built in Spring Boot started the same way: three weeks on organizations, roles,
> JWT refresh rotation, Stripe webhooks and plan limits. Every time. None of it is the product.
>
> So I built it once, properly, with the tests I wish I'd had — including one that proves a
> customer who knows another customer's record id still gets a 404.
>
> 40 tests, Java 21, Spring Boot 3.3. Details in the comments.

### Day 6–7 — Direct outreach
The highest-conversion channel, and the one everyone skips. Find 20 people building something in
Spring Boot — GitHub, Indie Hackers, r/SaaS — and send a *personal* message that leads with help:

> Saw you're building <their thing> on Spring Boot. I put together a starter kit covering the
> multi-tenancy and Stripe billing plumbing — happy to send you a free copy if you'd give me
> honest feedback on it.

Free copies to the first 5 are not lost revenue. They are testimonials, and testimonials are what
convert the next 50 buyers.

## 5. After launch

**Testimonials beat features.** After the first few sales, ask each buyer one question: *"What
would you have built instead of buying this?"* Their answer, quoted, is better copy than anything
you'd write.

**Let buyers tell you the roadmap.** Likely first requests, in order: email verification and
password reset, a React or Next.js frontend, Kotlin, an email-invitation flow, Keycloak/OAuth2.
Build the one asked for most, announce it as a free update to existing buyers, and raise the price
for new ones.

**Compound, don't relaunch.** Each new article ranks for another search term. The kit sells
steadily off content; it does not sell off a single launch day.

## 6. Reality check

Be clear-eyed, because the asymmetry here matters.

**The code is done and verified. The revenue is not, and nothing in this repo can promise it.**
Product quality is the part you can control, and it's handled. Distribution is the bottleneck,
and it's work.

Honest numbers. Conversion on a technical landing page runs roughly **1–3%** of targeted visitors.
So:

| Targeted visitors | Sales at 2% | Revenue at $99 |
|---|---|---|
| 500 | 10 | ~$990 |
| 2,000 | 40 | ~$3,960 |
| 10,000 | 200 | ~$19,800 |

The table is arithmetic, not a forecast. The hard part is the left column — and "targeted" is
doing real work there. 10,000 random visitors convert near zero; 500 Spring developers who came
from an article about Spring Boot billing convert well.

What plausibly happens:

- **Realistic first month:** 0–5 sales. Most launches are quiet. This is not failure, it's the
  base rate.
- **A good outcome:** one post lands, 20–50 sales in a week, then a steady trickle from search.
- **The likeliest failure mode is not the product. It's posting once, getting no traction, and
  stopping.** The people who make this work publish for months.

Two things that are true regardless of whether it sells:

1. **This code is reusable.** If nobody buys it, you still own a tested SaaS foundation for your
   own product — which was going to cost you three weeks.
2. **It's a credential.** "I built and sell a Spring Boot SaaS starter kit" is a stronger line on
   a contracting profile than most CVs carry, and consulting leads from it may well out-earn the
   licence sales.

**Before you take money from anyone:**

- [ ] Have `LICENSE.md` reviewed by a lawyer in your jurisdiction — it's a template, not advice
- [ ] Register for tax as your country requires, or use a merchant of record and let them handle it
- [ ] Write a refund policy and honour it
- [ ] Never claim it is "secure" or "compliant" in the abstract; describe what it does and let the
      tests speak
- [ ] Read Reddit and Hacker News self-promotion rules — a ban costs you your best channel

## 7. Your first three actions

1. Push to a **private** GitHub repo.
2. Create the Lemon Squeezy product: $99 solo, $249 team, `LAUNCH25` for $69.
3. Write the Day-1 Reddit post — the teaching one, not the selling one.

Everything else follows from those.
