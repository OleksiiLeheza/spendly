import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatListModule } from '@angular/material/list';
import { TranslocoPipe } from '@jsverse/transloco';
import { ProfileView } from '../../../shared/util/profile-view.model';

/** Presentational only: no injection, no fetching (CLAUDE.md 9.2). */
@Component({
  selector: 'app-profile-card',
  standalone: true,
  imports: [DatePipe, MatCardModule, MatChipsModule, MatListModule, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './profile-card.component.html',
})
export class ProfileCardComponent {
  readonly profile = input.required<ProfileView>();
}
