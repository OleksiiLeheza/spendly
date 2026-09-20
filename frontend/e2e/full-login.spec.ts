import { test, expect } from '@playwright/test';

const KEYCLOAK = process.env['E2E_KEYCLOAK_URL'] ?? 'http://localhost:8080';
const API = process.env['E2E_API_URL'] ?? 'http://localhost:8082';

/**
 * The whole journey: Angular -> Keycloak login -> code exchange -> authenticated API call.
 *
 * <p>Needs the Docker stack and user-service running, so it skips itself otherwise.
 */
test.describe('signing in with email and password', () => {
  // Both journeys sign in as the same seeded user; run them one after another so two
  // simultaneous logins never race each other in Keycloak's session handling.
  test.describe.configure({ mode: 'serial' });

  test.beforeEach(async () => {
    const [kc, api] = await Promise.all([
      fetch(`${KEYCLOAK}/realms/spendly/.well-known/openid-configuration`)
        .then((r) => r.ok)
        .catch(() => false),
      fetch(`${API.replace('8082', '9091')}/actuator/health`)
        .then((r) => r.ok)
        .catch(() => false),
    ]);
    test.skip(!kc || !api, 'Needs the Docker stack and user-service running');
  });

  test('lands on the dashboard with a profile loaded from the API', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('button', { name: 'Sign in with email' }).click();

    await page.waitForURL(/\/realms\/spendly\/protocol\/openid-connect\/auth/);
    await page.locator('#kc-form-login').waitFor();
    await page.locator('#username').fill('dev@spendly.local');
    await page.locator('#password').fill('dev');
    await page.locator('#kc-login').click();

    await page.waitForURL(/\/dashboard$/, { timeout: 20_000 });

    await expect(page.getByText('Signed in as dev@spendly.local')).toBeVisible();

    // A rendered profile id proves the API call succeeded with a real bearer token, not
    // merely that the redirect finished. It must also be the time-ordered UUIDv7 the
    // backend generates - the version nibble is the '7' opening the third group.
    await expect(
      page.getByText(/^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/),
    ).toBeVisible();

    await expect(page.getByText('Default currency')).toBeVisible();
    await expect(page.locator('mat-chip').filter({ hasText: 'USER' })).toBeVisible();
  });

  test('the seeded administrator signs in with the ADMIN role', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('button', { name: 'Sign in with email' }).click();
    await page.locator('#kc-form-login').waitFor();
    await page.locator('#username').fill('admin@gmail.com');
    await page.locator('#password').fill('admin');
    await page.locator('#kc-login').click();

    await page.waitForURL(/\/dashboard$/, { timeout: 20_000 });

    await expect(page.getByText('Signed in as admin@gmail.com')).toBeVisible();
    await expect(page.locator('mat-chip').filter({ hasText: 'ADMIN' })).toBeVisible();
  });

  test('signing out returns the user to the login page', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('button', { name: 'Sign in with email' }).click();
    await page.waitForURL(/\/realms\/spendly/);
    await page.locator('#kc-form-login').waitFor();
    await page.locator('#username').fill('dev@spendly.local');
    await page.locator('#password').fill('dev');
    await page.locator('#kc-login').click();
    await page.waitForURL(/\/dashboard$/, { timeout: 20_000 });

    await page.getByRole('button', { name: 'Sign out' }).click();

    await page.waitForURL(/localhost:4200\/(login)?$/, { timeout: 20_000 });
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login$/);
  });
});
