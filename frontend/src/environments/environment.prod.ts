export const environment = {
  production: true,
  apiBaseUrl: '/api-gateway',
  auth: {
    authority: 'https://auth.spendly.dev/realms/spendly',
    clientId: 'spendly-web',
    scope: 'openid profile email',
  },
};
