import { test, expect } from '@playwright/test';

const KEYCLOAK = process.env['E2E_KEYCLOAK_URL'] ?? 'http://localhost:8080';

test.describe('login page', () => {
  test('redirects an anonymous visitor away from the dashboard', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('offers both email and Google sign-in', async ({ page }) => {
    await page.goto('/login');

    await expect(page.getByRole('button', { name: 'Sign in with email' })).toBeEnabled();
    await expect(page.getByRole('button', { name: 'Continue with Google' })).toBeEnabled();
  });

  test('has no horizontal overflow at phone width', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 780 });
    await page.goto('/login');

    const overflows = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    );
    expect(overflows).toBe(false);
  });

  test('sends the user to Keycloak with PKCE parameters', async ({ page }) => {
    const reachable = await fetch(`${KEYCLOAK}/realms/spendly/.well-known/openid-configuration`)
      .then((r) => r.ok)
      .catch(() => false);
    test.skip(!reachable, 'Keycloak is not running; start the Docker stack first');

    await page.goto('/login');
    await page.getByRole('button', { name: 'Sign in with email' }).click();

    await page.waitForURL(/\/realms\/spendly\/protocol\/openid-connect\/auth/);
    const params = new URL(page.url()).searchParams;

    expect(params.get('client_id')).toBe('spendly-web');
    expect(params.get('response_type')).toBe('code');
    expect(params.get('code_challenge_method')).toBe('S256');
    expect(params.get('code_challenge')).toBeTruthy();
    // A public client must never ship a secret to the browser.
    expect(params.get('client_secret')).toBeNull();
  });

  test('the Google button jumps straight to the Google identity provider', async ({ page }) => {
    const reachable = await fetch(`${KEYCLOAK}/realms/spendly/.well-known/openid-configuration`)
      .then((r) => r.ok)
      .catch(() => false);
    test.skip(!reachable, 'Keycloak is not running; start the Docker stack first');

    await page.goto('/login');
    await page.getByRole('button', { name: 'Continue with Google' }).click();

    // With kc_idp_hint set, Keycloak skips its own provider chooser and forwards straight
    // to Google, so the browser never settles on the realm's auth page.
    await page.waitForURL(/accounts\.google\.com/, { timeout: 20_000 });

    const params = new URL(page.url()).searchParams;
    expect(params.get('client_id')).toBeTruthy();

    // Until real credentials are in .env the realm forwards the placeholder id and Google
    // rejects it. Reaching Google at all is what proves the broker is wired up.
    if (params.get('client_id') === 'google-client-id-not-set') {
      expect(page.url()).toContain('authError');
    }
  });
});
