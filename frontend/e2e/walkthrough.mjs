/**
 * End-to-end walkthrough of the whole product against a running server.
 *
 * Opt-in, because it needs a browser that the default install does not pull down:
 *   npm install --no-save playwright && npx playwright install chromium
 *   node e2e/walkthrough.mjs
 *
 * Point BASE_URL at the Vite dev server (http://localhost:5173) or at a packaged
 * jar (http://localhost:8080). It writes a screenshot per step, which is also the
 * quickest way to produce fresh marketing images.
 */
import { chromium } from 'playwright';

const BASE = process.env.BASE_URL ?? 'http://localhost:8080';
const email = `ada-${Date.now()}@acme.test`;
const log = [];
const step = (m) => { log.push(m); console.log(m); };

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1360, height: 900 } });
page.on('pageerror', (e) => step(`PAGE ERROR: ${e.message}`));
page.on('console', (m) => { if (m.type() === 'error') step(`CONSOLE ERROR: ${m.text()}`); });

// 1. Unauthenticated visit lands on sign-in.
await page.goto(BASE, { waitUntil: 'domcontentloaded' });
step(`1. redirected to: ${new URL(page.url()).pathname}`);
await page.screenshot({ path: 'shot-01-login.png' });

// 2. Register.
await page.getByRole('link', { name: 'Create one' }).click();
await page.getByLabel('Your name').fill('Ada Founder');
await page.getByLabel('Organization name').fill('Acme Rockets');
await page.getByLabel('Work email').fill(email);
await page.getByLabel('Password').fill('correct-horse-battery');
await page.getByRole('button', { name: /Create workspace/ }).click();
await page.waitForURL(`${BASE}/`, { timeout: 15000 });
await page.waitForSelector('text=Active projects');
step('2. registered and landed on dashboard');
await page.screenshot({ path: 'shot-02-dashboard.png' });

// 3. Create projects up to the free-plan limit of 3.
await page.getByRole('link', { name: 'Projects' }).click();
await page.waitForSelector('text=No projects yet');
for (const name of ['Falcon Telemetry', 'Ground Control', 'Payload Scheduler']) {
  await page.getByRole('banner').getByRole('button', { name: 'New project' }).click();
  const dialog = page.getByRole('dialog', { name: 'New project' });
  await dialog.getByLabel('Name').fill(name);
  await dialog.getByLabel('Description').fill(`${name} service`);
  await dialog.getByRole('button', { name: 'Create project' }).click();
  await page.waitForSelector(`td:has-text("${name}")`, { timeout: 10000 });
}
step('3. created 3 projects (free plan limit)');
await page.screenshot({ path: 'shot-03-projects.png' });

// 4. The fourth must produce the 402 upgrade prompt with real numbers.
await page.getByRole('banner').getByRole('button', { name: 'New project' }).click();
const overDialog = page.getByRole('dialog', { name: 'New project' });
await overDialog.getByLabel('Name').fill('One Too Many');
await overDialog.getByRole('button', { name: 'Create project' }).click();
await page.waitForSelector('text=Plan limit reached', { timeout: 10000 });
const quotaText = (await page.locator('.notice.quota').first().innerText()).replace(/\s+/g, ' ');
step(`4. quota prompt shown: "${quotaText}"`);
await page.screenshot({ path: 'shot-04-quota.png' });

// 5. The prompt's button navigates to billing.
await page.getByRole('button', { name: 'See plans' }).click();
await page.waitForSelector('text=Current plan: FREE');
step('5. "See plans" navigated to billing');
await page.screenshot({ path: 'shot-05-billing.png' });

// 6. API keys: plaintext shown exactly once.
await page.getByRole('link', { name: 'API keys' }).click();
await page.getByRole('banner').getByRole('button', { name: 'Create key' }).click();
const keyDialog = page.getByRole('dialog', { name: 'Create an API key' });
await keyDialog.getByLabel('Name').fill('ci-pipeline');
await keyDialog.getByRole('button', { name: 'Create key' }).click();
await page.waitForSelector('text=Copy your key now');
const secret = await page.locator('.secret').innerText();
step(`6. key issued, starts with: ${secret.slice(0, 14)}…`);
await page.screenshot({ path: 'shot-06-apikey.png' });
await page.getByRole('button', { name: 'Done' }).click();
await page.waitForSelector('td:has-text("ci-pipeline")');
const listing = await page.locator('table').innerText();
step(`7. plaintext absent from listing afterwards: ${!listing.includes(secret)}`);

// 7. Audit log is populated by the actions above.
await page.getByRole('link', { name: 'Audit log' }).click();
await page.waitForSelector('td:has-text("project.created")');
const rows = await page.locator('tbody tr').count();
step(`8. audit log has ${rows} entries`);
await page.screenshot({ path: 'shot-07-audit.png' });

// 8. Dark theme.
await page.getByRole('button', { name: /Switch to dark theme/ }).click();
await page.getByRole('link', { name: 'Overview' }).click();
await page.waitForSelector('text=Active projects');
step('9. dark theme applied');
await page.screenshot({ path: 'shot-08-dark.png' });

// 9. Hard refresh on a client route must survive.
await page.goto(`${BASE}/projects`, { waitUntil: 'domcontentloaded' });
await page.waitForSelector('td:has-text("Falcon Telemetry")', { timeout: 10000 });
step('10. hard refresh on /projects kept the session and rendered');

// 10. Mobile width.
await page.setViewportSize({ width: 390, height: 844 });
await page.reload({ waitUntil: 'domcontentloaded' });
await page.waitForSelector('td:has-text("Falcon Telemetry")');
const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1);
step(`11. horizontal page overflow at 390px: ${overflow}`);
await page.screenshot({ path: 'shot-09-mobile.png' });

await browser.close();

// A blocked Google Fonts request and the deliberate 402/401 responses show up here
// as console errors; anything else is worth looking at.
console.log('\n--- console output captured ---');
console.log(log.filter((l) => l.includes('ERROR')).join('\n') || 'none');
console.log('\nScreenshots written to the working directory.');
