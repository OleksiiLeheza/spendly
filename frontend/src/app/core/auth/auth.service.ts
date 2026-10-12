import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AccessTokenResponse, AuthenticationApi } from '../api/auth';
import { rememberReturnUrl } from './return-url';

export interface AuthenticatedUser {
  readonly subject: string;
  readonly email: string | null;
  readonly name: string | null;
  readonly roles: readonly string[];
}

export type SignInResult =
  | { readonly status: 'signed-in' }
  | { readonly status: 'two-factor'; readonly challengeToken: string };

export interface StepUpRequest {
  readonly resolve: (confirmed: boolean) => void;
}

interface TokenClaims {
  sub?: string;
  email?: string;
  preferred_username?: string;
  realm_access?: { roles?: string[] };
  exp?: number;
}

const RENEW_BEFORE_EXPIRY_SECONDS = 30;

/**
 * The browser's session: a short-lived access token held in memory and a long-lived cookie
 * that only auth-service can read. A reload loses the token and gets it back from the cookie.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(AuthenticationApi);
  private readonly router = inject(Router);

  private readonly accessToken = signal<string | null>(null);
  private readonly ready = signal(false);
  private readonly pendingStepUp = signal<StepUpRequest | null>(null);

  private renewTimer: ReturnType<typeof setTimeout> | null = null;
  private refreshing: Promise<boolean> | null = null;
  private stepUpPromise: Promise<boolean> | null = null;

  readonly isReady = this.ready.asReadonly();
  readonly isAuthenticated = computed(() => this.accessToken() !== null);
  readonly currentUser = computed(() => userFrom(this.accessToken()));
  readonly isAdmin = computed(() => this.currentUser()?.roles.includes('ADMIN') ?? false);
  readonly stepUpRequest = this.pendingStepUp.asReadonly();

  async initialize(): Promise<void> {
    await this.refresh();
    this.ready.set(true);
  }

  token(): string | null {
    return this.accessToken();
  }

  async signIn(email: string, password: string): Promise<SignInResult> {
    const response = await firstValueFrom(this.api.login({ email, password }));
    if (response.status === 'TWO_FACTOR_REQUIRED' && response.challengeToken) {
      return { status: 'two-factor', challengeToken: response.challengeToken };
    }
    this.apply({
      accessToken: response.accessToken ?? '',
      tokenType: 'Bearer',
      expiresIn: response.expiresIn ?? 0,
    });
    return { status: 'signed-in' };
  }

  async completeTwoFactor(challengeToken: string, code: string): Promise<void> {
    this.apply(await firstValueFrom(this.api.loginWithTwoFactor({ challengeToken, code })));
  }

  async register(email: string, password: string): Promise<void> {
    this.apply(await firstValueFrom(this.api.register({ email, password })));
  }

  signInWithGoogle(): void {
    window.location.assign(`${environment.api.auth}/api/v1/auth/google/start`);
  }

  async reauthenticate(password: string, code?: string): Promise<void> {
    this.apply(await firstValueFrom(this.api.reauthenticate({ password, code })));
  }

  refresh(): Promise<boolean> {
    this.refreshing ??= this.renew().finally(() => {
      this.refreshing = null;
    });
    return this.refreshing;
  }

  requestStepUp(): Promise<boolean> {
    this.stepUpPromise ??= new Promise<boolean>((resolve) => {
      this.pendingStepUp.set({
        resolve: (confirmed) => {
          this.pendingStepUp.set(null);
          this.stepUpPromise = null;
          resolve(confirmed);
        },
      });
    });
    return this.stepUpPromise;
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.api.logout());
    } catch {
      // The cookie expires on its own; the person is signed out here either way.
    }
    this.clear();
    await this.router.navigateByUrl('/login');
  }

  sessionExpired(): void {
    if (!this.isAuthenticated()) {
      return;
    }
    rememberReturnUrl(this.router.url);
    this.clear();
    void this.router.navigateByUrl('/login');
  }

  secondsLeft(): number | null {
    const exp = claimsOf(this.accessToken())?.exp;
    return exp === undefined ? null : exp - Date.now() / 1000;
  }

  private async renew(): Promise<boolean> {
    try {
      this.apply(await firstValueFrom(this.api.refresh()));
      return true;
    } catch {
      this.clear();
      return false;
    }
  }

  private apply(response: AccessTokenResponse): void {
    this.accessToken.set(response.accessToken);
    this.cancelRenewal();
    const lifetime = Math.max(response.expiresIn - RENEW_BEFORE_EXPIRY_SECONDS, 1);
    this.renewTimer = setTimeout(() => void this.refresh(), lifetime * 1000);
  }

  private clear(): void {
    this.cancelRenewal();
    this.accessToken.set(null);
  }

  private cancelRenewal(): void {
    if (this.renewTimer !== null) {
      clearTimeout(this.renewTimer);
      this.renewTimer = null;
    }
  }
}

function userFrom(token: string | null): AuthenticatedUser | null {
  const claims = claimsOf(token);
  if (claims === null) {
    return null;
  }
  return {
    subject: claims.sub ?? '',
    email: claims.email ?? null,
    name: claims.preferred_username ?? null,
    roles: claims.realm_access?.roles ?? [],
  };
}

function claimsOf(token: string | null): TokenClaims | null {
  return token === null ? null : decodeJwtPayload(token);
}

/**
 * Display purposes only. The signature is never checked here - the backend does that, and a
 * token this client trusted on its own word would prove nothing.
 */
export function decodeJwtPayload(token: string): TokenClaims | null {
  const payload = token?.split('.')[1];
  if (!payload) {
    return null;
  }
  try {
    const normalised = payload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalised.padEnd(normalised.length + ((4 - (normalised.length % 4)) % 4), '=');
    return JSON.parse(atob(padded)) as TokenClaims;
  } catch {
    return null;
  }
}
