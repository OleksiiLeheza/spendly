import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { AuthService } from '../../../core/auth/auth.service';
import { ProfileStore } from '../data-access/profile.store';
import { ProfileCardComponent } from '../ui/profile-card.component';
import { ProfileView } from '../../../shared/util/profile-view.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [MatButtonModule, MatCardModule, MatProgressBarModule, ProfileCardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.component.html',
  styles: `
    .page {
      display: grid;
      gap: 1rem;
      max-inline-size: 60rem;
      margin-inline: auto;
      padding: 1rem;
    }

    @media (min-width: 905px) {
      .page {
        padding: 2rem;
      }
    }
  `,
})
export class DashboardComponent {
  private readonly auth = inject(AuthService);
  protected readonly store = inject(ProfileStore);

  /** Maps the generated API model onto the presentation contract the card accepts. */
  protected readonly view = computed<ProfileView | null>(() => {
    const profile = this.store.profile();
    if (!profile) {
      return null;
    }
    return {
      id: profile.id,
      email: profile.email,
      displayName: profile.displayName,
      defaultCurrency: profile.defaultCurrency,
      identityProvider: profile.identityProvider ?? null,
      createdAt: profile.createdAt,
      roles: this.auth.currentUser()?.roles ?? [],
    };
  });

  constructor() {
    this.store.load();
  }

  protected retry(): void {
    this.store.load();
  }
}
