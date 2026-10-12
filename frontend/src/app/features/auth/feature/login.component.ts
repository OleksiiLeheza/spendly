import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService } from '../../../core/auth/auth.service';
import { takeReturnUrl } from '../../../core/auth/return-url';
import { LANGUAGES, Lang, LanguageService } from '../../../core/i18n/language.service';
import { AuthStep, authErrorKey } from '../data-access/auth-errors';
import { ProvidersService } from '../data-access/providers.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    MatButtonModule,
    MatButtonToggleModule,
    MatCardModule,
    MatDividerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    ReactiveFormsModule,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.component.html',
  styles: `
    .login {
      display: grid;
      min-block-size: 100dvh;
    }

    .login__hero {
      display: grid;
      grid-template-rows: auto 1fr;
      gap: 2rem;
      padding: 1.5rem 1rem;
      background: var(--mat-sys-surface-container);
    }

    .login__brand {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font: var(--mat-sys-title-large);
      color: var(--mat-sys-primary);
    }

    .login__pitch {
      display: grid;
      align-self: center;
      gap: 0.75rem;
      max-inline-size: 34rem;
    }

    .login__headline {
      margin: 0;
      font: var(--mat-sys-headline-medium);
    }

    .login__lead,
    .login__feature-title,
    .login__feature-text,
    .login__explain {
      margin: 0;
    }

    .login__lead {
      font: var(--mat-sys-body-large);
      color: var(--mat-sys-on-surface-variant);
    }

    .login__features {
      display: none;
      gap: 1.25rem;
      margin: 2rem 0 0;
      padding: 0;
      list-style: none;
    }

    .login__feature {
      display: grid;
      grid-template-columns: auto 1fr;
      gap: 1rem;
    }

    .login__feature-icon {
      color: var(--mat-sys-primary);
    }

    .login__feature-title {
      font: var(--mat-sys-title-small);
    }

    .login__feature-text {
      font: var(--mat-sys-body-medium);
      color: var(--mat-sys-on-surface-variant);
    }

    .login__panel {
      display: grid;
      justify-items: center;
      align-content: center;
      gap: 1.5rem;
      padding: 1.5rem 1rem 2rem;
    }

    .login__language {
      justify-self: end;
    }

    .login__card {
      inline-size: 100%;
      max-inline-size: 26rem;
    }

    .login__content {
      display: grid;
      gap: 0.75rem;
      padding-block-start: 1.5rem;
    }

    .login__form {
      display: grid;
      gap: 0.25rem;
    }

    .login__error {
      margin: 0;
      font: var(--mat-sys-body-medium);
      color: var(--mat-sys-error);
    }

    .login__hint {
      margin: 0;
      font: var(--mat-sys-body-medium);
      color: var(--mat-sys-on-surface-variant);
    }

    .login__link {
      justify-self: center;
    }

    .login__separator {
      display: grid;
      grid-template-columns: 1fr auto 1fr;
      align-items: center;
      gap: 0.75rem;
      font: var(--mat-sys-label-medium);
      color: var(--mat-sys-on-surface-variant);
    }

    .login__footer {
      display: grid;
      grid-template-columns: auto 1fr;
      gap: 0.75rem;
      padding: 1rem;
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
    }

    .login__google-mark {
      inline-size: 18px;
      block-size: 18px;
    }

    @media (min-width: 960px) {
      .login {
        grid-template-columns: minmax(0, 5fr) minmax(0, 6fr);
      }

      .login__hero {
        padding: 3rem;
      }

      .login__headline {
        font: var(--mat-sys-display-small);
        color: var(--mat-sys-primary);
      }

      .login__features {
        display: grid;
      }

      .login__panel {
        grid-template-rows: auto 1fr;
        padding: 1.5rem 3rem 3rem;
      }

      .login__card {
        align-self: center;
      }
    }
  `,
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly forms = inject(FormBuilder);

  readonly error = input<string>();

  protected readonly language = inject(LanguageService);
  protected readonly languages = LANGUAGES;
  protected readonly isReady = this.auth.isReady;
  protected readonly googleOffered = inject(ProvidersService).google;
  protected readonly features = [
    { key: 'ledger', icon: 'receipt_long' },
    { key: 'budgets', icon: 'donut_large' },
    { key: 'goals', icon: 'savings' },
    { key: 'shared', icon: 'group' },
  ] as const;

  protected readonly registering = signal(false);
  protected readonly step = signal<AuthStep>('credentials');
  protected readonly busy = signal(false);
  protected readonly errorKey = signal<string | null>(null);

  protected readonly credentials = this.forms.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });
  protected readonly verification = this.forms.nonNullable.group({
    code: ['', [Validators.required]],
  });

  private challengeToken: string | null = null;

  constructor() {
    effect(() => {
      if (this.auth.isAuthenticated()) {
        void this.router.navigateByUrl(takeReturnUrl() ?? '/dashboard', { replaceUrl: true });
      }
    });
  }

  protected async submitCredentials(): Promise<void> {
    if (this.credentials.invalid || this.busy()) {
      this.credentials.markAllAsTouched();
      return;
    }
    const { email, password } = this.credentials.getRawValue();
    this.busy.set(true);
    this.errorKey.set(null);
    try {
      if (this.registering()) {
        await this.auth.register(email, password);
        return;
      }
      const result = await this.auth.signIn(email, password);
      if (result.status === 'two-factor') {
        this.challengeToken = result.challengeToken;
        this.step.set('code');
      }
    } catch (error) {
      this.errorKey.set(authErrorKey(error, 'credentials'));
    } finally {
      this.busy.set(false);
    }
  }

  protected async submitCode(): Promise<void> {
    if (this.verification.invalid || this.busy() || this.challengeToken === null) {
      this.verification.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.errorKey.set(null);
    try {
      await this.auth.completeTwoFactor(this.challengeToken, this.verification.getRawValue().code);
    } catch (error) {
      this.errorKey.set(authErrorKey(error, 'code'));
    } finally {
      this.busy.set(false);
    }
  }

  protected backToCredentials(): void {
    this.challengeToken = null;
    this.verification.reset();
    this.errorKey.set(null);
    this.step.set('credentials');
  }

  protected toggleRegistering(): void {
    this.registering.update((registering) => !registering);
    this.errorKey.set(null);
  }

  protected signInWithGoogle(): void {
    this.auth.signInWithGoogle();
  }

  protected async chooseLanguage(lang: Lang): Promise<void> {
    await this.language.use(lang);
  }
}
