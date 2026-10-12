/**
 * Dev configuration.
 *
 * <p>Nothing secret belongs here: this file is bundled and served to the browser.
 *
 * <p>Every call goes to the gateway, which is the only public entry point (CLAUDE.md 3): it
 * validates the token, rate-limits per subject and routes by path. The per-service entries
 * stay separate because each generated client takes its own BASE_PATH, and pointing one of
 * them at its service's port is how you bypass the gateway while debugging that service.
 */
const gateway = 'http://localhost:8081';

export const environment = {
  production: false,
  api: {
    auth: gateway,
    user: gateway,
    account: gateway,
    transaction: gateway,
    budget: gateway,
    recurring: gateway,
    import: gateway,
    goal: gateway,
    notification: gateway,
    analytics: gateway,
    sharing: gateway,
  },
};
