import {
  ApplicationConfig,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
  inject,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { authInterceptor, provideAuth } from 'angular-auth-oidc-client';

import { routes } from './app.routes';
import { authConfig } from './core/auth/auth.config';
import { AuthService } from './core/auth/auth.service';
import { provideApi } from './core/api-client';
import { environment } from '../environments/environment';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes, withComponentInputBinding()),

    // authInterceptor attaches the bearer token, and only to authConfig's secureRoutes.
    provideHttpClient(withFetch(), withInterceptors([authInterceptor()])),
    provideAuth(authConfig),
    provideApi(environment.apiBaseUrl),

    // Resolve the session before the first route activates, so authGuard never sees a
    // half-initialised state and bounce a signed-in user back to the login page.
    provideAppInitializer(() => inject(AuthService).initialize()),
  ],
};
