import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatListModule } from '@angular/material/list';
import { ProfileView } from '../../../shared/util/profile-view.model';

/** Presentational only: no injection, no fetching (CLAUDE.md 9.2). */
@Component({
  selector: 'app-profile-card',
  standalone: true,
  imports: [DatePipe, MatCardModule, MatChipsModule, MatListModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './profile-card.component.html',
  styles: `
    .chips {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
  `,
})
export class ProfileCardComponent {
  readonly profile = input.required<ProfileView>();
}
