import { test, expect } from './fixtures';
import { NEEDS_STACK, stackIsUp } from './stack';

/**
 * Self-registration: the "Create account" form takes an email and a password, signs the new
 * account in, and user-service provisions its profile on the first authenticated request.
 */
test.describe('creating an account', () => {
  test.beforeEach(async () => {
    test.skip(!(await stackIsUp()), NEEDS_STACK);
  });

  test('the login page offers sign-up next to sign-in', async ({ page }) => {
    await page.goto('/login');

    await page.getByRole('button', { name: 'New to Spendly? Create account' }).click();

    await expect(page.getByText('Create your account')).toBeVisible();
  });

  test('a short password is refused', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('button', { name: 'New to Spendly? Create account' }).click();
    await page.getByLabel('Email').fill(`short-${Date.now()}@spendly.local`);
    await page.getByLabel('Password').fill('short');
    await page.getByRole('button', { name: 'Create account', exact: true }).click();

    await expect(page.getByRole('alert')).toContainText('at least 10 characters');
  });

  test('asks only for an email and a password, then signs the new person in', async ({ page }) => {
    const email = `signup-${Date.now()}@spendly.local`;

    await page.goto('/login');
    await page.getByRole('button', { name: 'New to Spendly? Create account' }).click();
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Password').fill('correct horse battery staple');
    await page.getByRole('button', { name: 'Create account', exact: true }).click();

    await page.waitForURL(/\/(dashboard|onboarding\/account)$/, { timeout: 20_000 });
    expect(page.url()).not.toContain('/login');
  });

  test('an email that is already registered is refused', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('button', { name: 'New to Spendly? Create account' }).click();
    await page.getByLabel('Email').fill('dev@spendly.local');
    await page.getByLabel('Password').fill('correct horse battery staple');
    await page.getByRole('button', { name: 'Create account', exact: true }).click();

    await expect(page.getByRole('alert')).toContainText('already registered');
  });
});
