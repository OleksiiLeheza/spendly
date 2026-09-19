import { PassedInitialConfig, LogLevel } from 'angular-auth-oidc-client';
import { environment } from '../../../environments/environment';

/**
 * Authorization Code + PKCE against the Keycloak `spendly` realm (FI-22).
 *
 * <p>No client secret: the SPA is a public client, so possession of the code verifier is what
 * proves the token request came from the browser that started the login.
 */
export const authConfig: PassedInitialConfig = {
  config: {
    authority: environment.auth.authority,
    redirectUrl: `${window.location.origin}/callback`,
    postLogoutRedirectUri: window.location.origin,
    clientId: environment.auth.clientId,
    scope: environment.auth.scope,
    responseType: 'code',

    // Keycloak's realm sets revokeRefreshToken with refreshTokenMaxReuse=0, so each refresh
    // returns a new token and invalidates the old one. Replaying a stolen refresh token then
    // kills the session instead of extending it.
    silentRenew: true,
    useRefreshToken: true,
    renewTimeBeforeTokenExpiresInSeconds: 30,

    // The library warns that refresh tokens usually need the `offline_access` scope. They do
    // not here: Keycloak issues a refresh token for the standard flow, bounded by the SSO
    // session. An offline token would instead survive logout and browser close, which is the
    // wrong trade for an app holding financial data - so the warning is silenced, not obeyed.
    disableRefreshTokenOfflineAccessScopeWarning: true,

    // Tokens are kept in memory, not localStorage: anything in localStorage is readable by
    // any script that manages to run on the origin.
    ignoreNonceAfterRefresh: true,

    // Attach the access token only to our own API. Sending it anywhere else would leak it.
    secureRoutes: [environment.apiBaseUrl],

    logLevel: environment.production ? LogLevel.Error : LogLevel.Warn,
  },
};
