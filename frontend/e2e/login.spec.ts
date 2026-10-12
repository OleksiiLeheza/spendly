import { test, expect } from './fixtures';
import { NEEDS_STACK, stackIsUp } from './stack';

test.describe('login page', () => {
  test('redirects an anonymous visitor away from the dashboard', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('asks for an email and a password', async ({ page }) => {
    await page.goto('/login');

    await expect(page.getByLabel('Email')).toBeVisible();
    await expect(page.getByLabel('Password')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
    await expect(
      page.getByRole('button', { name: 'New to Spendly? Create account' }),
    ).toBeVisible();
  });

  test('has no horizontal overflow at phone width', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 780 });
    await page.goto('/login');

    const overflows = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    );
    expect(overflows).toBe(false);
  });

  test('a wrong password says so and keeps the person on the page', async ({ page }) => {
    test.skip(!(await stackIsUp()), NEEDS_STACK);

    await page.goto('/login');
    await page.getByLabel('Email').fill('dev@spendly.local');
    await page.getByLabel('Password').fill('not the password');
    await page.getByRole('button', { name: 'Sign in' }).click();

    await expect(page.getByRole('alert')).toHaveText('The email or the password is not right.');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('the Google button appears only when Google is configured', async ({ page }) => {
    test.skip(!(await stackIsUp()), NEEDS_STACK);
    const providers = await page.request
      .get(`${process.env['E2E_GATEWAY_URL'] ?? 'http://localhost:8081'}/api/v1/auth/providers`)
      .then((response) => response.json() as Promise<{ google: boolean }>);

    await page.goto('/login');

    const google = page.getByRole('button', { name: 'Continue with Google' });
    if (providers.google) {
      await expect(google).toBeEnabled();
    } else {
      await expect(google).toHaveCount(0);
    }
  });
});
