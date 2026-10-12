import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService } from '../../../core/auth/auth.service';
import { takeReturnUrl } from '../../../core/auth/return-url';

/**
 * Landing spot after Google sends the browser back. auth-service has already set the session
 * cookie, and app initialisation traded it for a token, so this only waits for that to settle and
 * forwards the person.
 */
@Component({
  selector: 'app-callback',
  standalone: true,
  imports: [MatProgressSpinnerModule, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="callback">
      <mat-spinner diameter="40" />
      <p>{{ 'auth.signingIn' | transloco }}</p>
    </main>
  `,
  styles: `
    .callback {
      display: grid;
      place-items: center;
      align-content: center;
      gap: 1rem;
      min-block-size: 100dvh;
    }
  `,
})
export class CallbackComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  constructor() {
    effect(() => {
      if (!this.auth.isReady()) {
        return;
      }
      const target = this.auth.isAuthenticated() ? (takeReturnUrl() ?? '/dashboard') : '/login';
      void this.router.navigateByUrl(target, { replaceUrl: true });
    });
  }
}
