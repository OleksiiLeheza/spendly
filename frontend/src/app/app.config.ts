import {
  ApplicationConfig,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  isDevMode,
  provideZonelessChangeDetection,
  inject,
} from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeUk from '@angular/common/locales/uk';
import {
  TitleStrategy,
  provideRouter,
  withComponentInputBinding,
  withInMemoryScrolling,
} from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideTransloco } from '@jsverse/transloco';
import { provideTranslocoMessageformat } from '@jsverse/transloco-messageformat';

import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { authInterceptor } from './core/auth/auth.interceptor';
import { stepUpInterceptor } from './core/auth/step-up.interceptor';
import { LanguageService } from './core/i18n/language.service';
import { languageInterceptor } from './core/i18n/language.interceptor';
import { TranslatedTitleStrategy } from './core/i18n/translated-title.strategy';
import { ThemeService } from './core/theme/theme.service';
import { TranslocoHttpLoader } from './core/i18n/transloco-loader';
import { provideApi as provideAuthApi } from './core/api/auth';
import { provideApi as provideUserApi } from './core/api/user';
import { provideApi as provideAccountApi } from './core/api/account';
import { provideApi as provideTransactionApi } from './core/api/transaction';
import { provideApi as provideBudgetApi } from './core/api/budget';
import { provideApi as provideRecurringApi } from './core/api/recurring';
import { provideApi as provideImportApi } from './core/api/import';
import { provideApi as provideGoalApi } from './core/api/goal';
import { provideApi as provideNotificationApi } from './core/api/notification';
import { provideApi as provideAnalyticsApi } from './core/api/analytics';
import { provideApi as provideSharingApi } from './core/api/sharing';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { environment } from '../environments/environment';

registerLocaleData(localeUk);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top' }),
    ),

    // authInterceptor attaches the bearer token to our own APIs only and renews it when it has just
    // expired. stepUpInterceptor sits after it and turns the server's step-up challenge into a
    // password prompt rather than an error the user cannot act on.
    provideHttpClient(
      withFetch(),
      withInterceptors([authInterceptor, languageInterceptor, stepUpInterceptor]),
    ),

    // Runtime language switching (CLAUDE.md 9.1): the dictionaries are static JSON, the ICU
    // plugin gives Ukrainian its one/few/many plural forms.
    provideTransloco({
      config: {
        availableLangs: ['en', 'uk'],
        defaultLang: 'en',
        fallbackLang: 'en',
        reRenderOnLangChange: true,
        prodMode: !isDevMode(),
      },
      loader: TranslocoHttpLoader,
    }),
    provideTranslocoMessageformat({ locales: ['en', 'uk'] }),

    // Each generated client declares its own BASE_PATH token, so one call per service.
    // The session cookie travels cross-origin to the gateway, so this client sends credentials.
    provideAuthApi({ basePath: environment.api.auth, withCredentials: true }),
    provideUserApi(environment.api.user),
    provideAccountApi(environment.api.account),
    provideTransactionApi(environment.api.transaction),
    provideBudgetApi(environment.api.budget),
    provideRecurringApi(environment.api.recurring),
    provideImportApi(environment.api.import),
    provideGoalApi(environment.api.goal),
    provideNotificationApi(environment.api.notification),
    provideAnalyticsApi(environment.api.analytics),
    provideSharingApi(environment.api.sharing),

    // Restore the session from its cookie before the first route activates, so authGuard never sees
    // a half-initialised state and bounces a signed-in user back to the login page.
    provideAppInitializer(() => inject(AuthService).initialize()),
    provideAppInitializer(() => inject(LanguageService).initialize()),
    provideAppInitializer(() => inject(ThemeService).initialize()),
    { provide: TitleStrategy, useExisting: TranslatedTitleStrategy },
    { provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { appearance: 'outline' } },
  ],
};
