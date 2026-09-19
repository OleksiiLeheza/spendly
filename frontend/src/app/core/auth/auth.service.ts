import { Injectable, computed, inject, signal } from '@angular/core';
import { LoginResponse, OidcSecurityService } from 'angular-auth-oidc-client';
import { Observable, tap } from 'rxjs';

export interface AuthenticatedUser {
  readonly subject: string;
  readonly email: string | null;
  readonly name: string | null;
  readonly roles: readonly string[];
}

interface KeycloakClaims {
  sub?: string;
  email?: string;
  name?: string;
  preferred_username?: string;
  realm_access?: { roles?: string[] };
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oidc = inject(OidcSecurityService);

  private readonly authenticated = signal(false);
  private readonly user = signal<AuthenticatedUser | null>(null);
  private readonly ready = signal(false);

  readonly isAuthenticated = this.authenticated.asReadonly();
  readonly currentUser = this.user.asReadonly();
  readonly isReady = this.ready.asReadonly();
  readonly isAdmin = computed(() => this.user()?.roles.includes('ADMIN') ?? false);

  /**
   * `checkAuth()` is cold and, on the callback route, consumes the single-use authorization
   * code. Subscribing here as well as returning it would run the exchange twice and the
   * second attempt would fail, so the app initializer provides the only subscription.
   */
  initialize(): Observable<LoginResponse> {
    return this.oidc.checkAuth().pipe(
      tap(({ isAuthenticated, userData, accessToken }) => {
        this.authenticated.set(isAuthenticated);
        this.user.set(isAuthenticated ? toUser(userData, accessToken) : null);
        this.ready.set(true);
      }),
    );
  }

  login(): void {
    this.oidc.authorize();
  }

  /** `kc_idp_hint` is a Keycloak extension that skips the provider chooser. */
  loginWithGoogle(): void {
    this.oidc.authorize(undefined, { customParams: { kc_idp_hint: 'google' } });
  }

  logout(): void {
    this.oidc.logoff().subscribe();
  }
}

function toUser(userData: unknown, accessToken: string): AuthenticatedUser {
  const profile = (userData ?? {}) as KeycloakClaims;
  const claims = decodeJwtPayload(accessToken);

  return {
    subject: profile.sub ?? claims?.sub ?? '',
    email: profile.email ?? claims?.email ?? null,
    name: profile.name ?? profile.preferred_username ?? claims?.preferred_username ?? null,
    roles: claims?.realm_access?.roles ?? [],
  };
}

/**
 * Display purposes only. The signature is never checked here - the backend does that, and a
 * token this client trusted on its own word would prove nothing.
 */
function decodeJwtPayload(token: string): KeycloakClaims | null {
  const payload = token?.split('.')[1];
  if (!payload) {
    return null;
  }
  try {
    const normalised = payload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalised.padEnd(normalised.length + ((4 - (normalised.length % 4)) % 4), '=');
    return JSON.parse(atob(padded)) as KeycloakClaims;
  } catch {
    return null;
  }
}
