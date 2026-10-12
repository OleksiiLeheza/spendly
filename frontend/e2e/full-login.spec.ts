import type { Page } from '@playwright/test';
import { test, expect } from './fixtures';
import { NEEDS_STACK, stackIsUp } from './stack';

/**
 * The whole journey: Angular -> auth-service sign-in -> access token -> authenticated calls
 * through the gateway, with the session kept in an HttpOnly cookie.
 */
async function signIn(page: Page, email: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
}

test.describe('signing in with email and password', () => {
  test.beforeEach(async () => {
    test.skip(!(await stackIsUp()), NEEDS_STACK);
  });

  test('lands on the dashboard with a profile loaded from the API', async ({ page }) => {
    await signIn(page, 'dev@spendly.local', 'dev');

    await page.waitForURL(/\/dashboard$/, { timeout: 20_000 });

    await expect(page.getByRole('heading', { name: 'Overview' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Overview' })).toBeVisible();

    // The overview only renders once its authenticated calls to account-, budget-,
    // recurring- and transaction-service have resolved, so these four cards prove the
    // bearer token was accepted across the whole back end, not merely that the form posted.
    for (const card of ['Balances', 'Spent this month', 'Due in 30 days', 'Needs attention']) {
      await expect(page.getByText(card, { exact: true })).toBeVisible();
    }
  });

  test('the seeded administrator signs in with the ADMIN role', async ({ page }) => {
    await signIn(page, 'admin@gmail.com', 'admin');

    await page.waitForURL(/\/(dashboard|onboarding\/account)$/, { timeout: 20_000 });

    await page.goto('/settings');
    await expect(page.getByText('Developer tools')).toBeVisible();
  });

  test('a reload keeps the person signed in through the session cookie', async ({ page }) => {
    await signIn(page, 'dev@spendly.local', 'dev');
    await page.waitForURL(/\/dashboard$/, { timeout: 20_000 });

    await page.reload();

    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByRole('heading', { name: 'Overview' })).toBeVisible();
  });

  test('signing out returns the user to the login page', async ({ page }) => {
    await signIn(page, 'dev@spendly.local', 'dev');
    await page.waitForURL(/\/dashboard$/, { timeout: 20_000 });

    await page.getByRole('button', { name: 'Sign out' }).click();

    await page.waitForURL(/\/login$/, { timeout: 20_000 });
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login$/);
  });
});
