import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [MatButtonModule, MatCardModule, MatDividerModule, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.component.html',
  styles: `
    .login {
      display: grid;
      place-items: center;
      min-block-size: 100dvh;
      padding: 1rem;
    }

    .login__card {
      inline-size: 100%;
      max-inline-size: 26rem;
    }

    .login__content {
      display: grid;
      gap: 1rem;
    }

    .login__separator {
      display: grid;
      grid-template-columns: 1fr auto 1fr;
      align-items: center;
      gap: 0.75rem;
    }

    .login__google-mark {
      inline-size: 18px;
      block-size: 18px;
    }
  `,
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly isReady = this.auth.isReady;

  constructor() {
    effect(() => {
      if (this.auth.isAuthenticated()) {
        void this.router.navigate(['/dashboard']);
      }
    });
  }

  protected signIn(): void {
    this.auth.login();
  }

  protected signInWithGoogle(): void {
    this.auth.loginWithGoogle();
  }

  protected register(): void {
    this.auth.register();
  }
}
