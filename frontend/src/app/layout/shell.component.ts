import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslocoPipe } from '@jsverse/transloco';
import { map } from 'rxjs';
import { AuthService } from '../core/auth/auth.service';
import { UnreadNotificationsService } from '../core/notifications/unread-notifications.service';

interface NavItem {
  readonly icon: string;
  readonly labelKey: string;
  readonly path: string;
}

const NAV: readonly NavItem[] = [
  { icon: 'dashboard', labelKey: 'nav.overview', path: '/dashboard' },
  { icon: 'receipt_long', labelKey: 'nav.transactions', path: '/transactions' },
  { icon: 'account_balance_wallet', labelKey: 'nav.accounts', path: '/accounts' },
  { icon: 'data_usage', labelKey: 'nav.budgets', path: '/budgets' },
  { icon: 'groups', labelKey: 'nav.shared', path: '/shared-expenses' },
  { icon: 'flag', labelKey: 'nav.goals', path: '/goals' },
  { icon: 'insights', labelKey: 'nav.insights', path: '/insights' },
  { icon: 'label', labelKey: 'nav.categories', path: '/categories' },
  { icon: 'event_repeat', labelKey: 'nav.recurring', path: '/recurring' },
];
import { SessionExpiryService } from './session-expiry.service';
import { StepUpService } from './step-up.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatBadgeModule,
    MatButtonModule,
    MatIconModule,
    MatListModule,
    MatSidenavModule,
    MatToolbarModule,
    MatTooltipModule,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shell.component.html',
  styles: `
    .shell {
      block-size: 100dvh;
    }

    .shell__drawer {
      inline-size: 15rem;
      border-inline-end: 1px solid var(--mat-sys-outline-variant);
    }

    .shell__brand {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 1rem 1.25rem;
      font: var(--mat-sys-title-medium);
    }

    .shell__content {
      display: flex;
      flex-direction: column;
      block-size: 100dvh;
    }

    .shell__main {
      flex-grow: 1;
      min-block-size: 0;
      overflow: auto;
      padding-block-end: 1rem;
    }

    .shell__spacer {
      flex-grow: 1;
    }

    .shell__user {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.75rem 1.25rem;
      border-block-start: 1px solid var(--mat-sys-outline-variant);
      color: inherit;
      text-decoration: none;
    }

    .shell__user-name {
      display: grid;
      min-inline-size: 0;
    }

    .shell__user-name span {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .shell__user-email {
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class ShellComponent {
  private readonly auth = inject(AuthService);
  private readonly breakpoints = inject(BreakpointObserver);

  protected readonly user = this.auth.currentUser;
  protected readonly unread = inject(UnreadNotificationsService).count;
  protected readonly navItems = NAV;
  protected readonly isHandset = toSignal(
    this.breakpoints.observe(Breakpoints.Handset).pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  constructor() {
    inject(UnreadNotificationsService).start();
    inject(SessionExpiryService).watch();
    inject(StepUpService).watch();
  }

  protected closeOnHandset(drawer: MatSidenav): void {
    if (this.isHandset()) {
      void drawer.close();
    }
  }

  protected signOut(): void {
    void this.auth.logout();
  }
}
