import { test, expect } from '@playwright/test';

const KEYCLOAK = process.env['E2E_KEYCLOAK_URL'] ?? 'http://localhost:8080';
const API = process.env['E2E_API_URL'] ?? 'http://localhost:8082';

/**
 * Self-registration: the "Create account" button opens Keycloak's sign-up form via
 * `prompt=create`, the realm's user profile keeps that form to email + password, and the new
 * identity is provisioned by user-service on its first authenticated request.
 *
 * <p>Needs the Docker stack and user-service running, so it skips itself otherwise.
 */
test.describe('creating an account', () => {
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

  test('the login page offers sign-up next to sign-in', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('button', { name: 'Create account' })).toBeEnabled();
  });

  test('asks only for an email and a password, then lands on the dashboard', async ({ page }) => {
    // A fresh address every run: Keycloak refuses duplicate emails, and a registered user
    // stays in the realm until `docker compose down -v`.
    const email = `signup-${Date.now()}@spendly.local`;

    await page.goto('/login');
    await page.getByRole('button', { name: 'Create account' }).click();

    await page.waitForURL(/\/realms\/spendly\/protocol\/openid-connect\/auth/);
    expect(new URL(page.url()).searchParams.get('prompt')).toBe('create');
    await page.locator('#kc-register-form').waitFor();

    // First and last name are optional in the realm's user profile, so the form must not
    // demand them - a required name field would fail this submit with a validation error.
    await page.locator('#email').fill(email);
    await page.locator('#password').fill('correct horse battery staple');
    await page.locator('#password-confirm').fill('correct horse battery staple');
    await page.locator('#kc-form-buttons input[type=submit], #kc-form-buttons button').click();

    await page.waitForURL(/\/dashboard$/, { timeout: 20_000 });

    await expect(page.getByText(`Signed in as ${email}`)).toBeVisible();
    // The provisioned profile id proves user-service accepted the brand-new identity.
    await expect(
      page.getByText(/^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/),
    ).toBeVisible();
    await expect(page.locator('mat-chip').filter({ hasText: 'USER' })).toBeVisible();
    await expect(page.locator('mat-chip').filter({ hasText: 'ADMIN' })).toHaveCount(0);
  });
});
