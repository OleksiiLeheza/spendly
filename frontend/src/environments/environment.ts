/**
 * Dev configuration.
 *
 * <p>Nothing secret belongs here: this file is bundled and served to the browser. The SPA is
 * a public OAuth client, which is exactly why it uses PKCE instead of a client secret.
 */
export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8082',
  auth: {
    authority: 'http://localhost:8080/realms/spendly',
    clientId: 'spendly-web',
    scope: 'openid profile email',
  },
};
