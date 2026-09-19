import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { AuthService } from '../core/auth/auth.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, MatButtonModule, MatToolbarModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-toolbar>
      <span>Spendly</span>
      <span class="spacer"></span>
      <button matButton type="button" (click)="signOut()">Sign out</button>
    </mat-toolbar>
    <router-outlet />
  `,
  styles: `
    .spacer {
      flex: 1 1 auto;
    }
  `,
})
export class ShellComponent {
  private readonly auth = inject(AuthService);

  protected signOut(): void {
    this.auth.logout();
  }
}
