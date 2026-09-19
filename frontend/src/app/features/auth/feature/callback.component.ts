import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/auth/auth.service';

/**
 * Landing spot for Keycloak's redirect. The code-for-token exchange already ran during app
 * initialisation, so this only waits for it to settle and forwards the user.
 */
@Component({
  selector: 'app-callback',
  standalone: true,
  imports: [MatProgressSpinnerModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="callback">
      <mat-spinner diameter="40" />
      <p>Signing you in…</p>
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
      void this.router.navigate([this.auth.isAuthenticated() ? '/dashboard' : '/login'], {
        replaceUrl: true,
      });
    });
  }
}
