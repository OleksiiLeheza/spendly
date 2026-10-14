import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
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
import { initials } from '../shared/util/initials';

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
    }

    .rail {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
      min-block-size: 100%;
      padding: 1rem 0.75rem;
    }

    .rail__brand {
      display: flex;
      align-items: center;
      gap: 0.625rem;
      padding: 0.25rem 0.5rem;
      color: var(--app-on-nav);
      font: var(--mat-sys-title-large);
      text-decoration: none;
    }

    .rail__logo {
      display: grid;
      place-items: center;
      inline-size: 2rem;
      block-size: 2rem;
      border-radius: var(--app-control-shape);
      background-color: var(--mat-sys-primary);
      color: var(--mat-sys-on-primary);
    }

    .rail__logo mat-icon {
      font-size: 1.25rem;
    }

    .rail__count {
      display: inline-block;
      min-inline-size: 1.5rem;
      padding-inline: 0.375rem;
      border-radius: var(--mat-sys-corner-full);
      background-color: var(--mat-sys-primary);
      color: var(--mat-sys-on-primary);
      font: var(--mat-sys-label-small);
      line-height: 1.25rem;
      text-align: center;
    }

    .rail__spacer,
    .shell__spacer {
      flex-grow: 1;
    }

    .rail__user {
      display: flex;
      align-items: center;
      gap: 0.25rem;
      padding: 0.75rem 0 0 0.25rem;
      border-block-start: 1px solid var(--mat-sys-outline-variant);
    }

    .rail__profile {
      display: flex;
      flex-grow: 1;
      align-items: center;
      gap: 0.625rem;
      min-inline-size: 0;
      padding: 0.25rem;
      border-radius: var(--app-control-shape);
      color: var(--app-on-nav);
      text-decoration: none;
    }

    .rail__avatar {
      display: grid;
      flex-shrink: 0;
      place-items: center;
      inline-size: 2rem;
      block-size: 2rem;
      border-radius: var(--mat-sys-corner-full);
      background-color: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
      font: var(--mat-sys-label-medium);
    }

    .rail__who {
      display: grid;
      min-inline-size: 0;
    }

    .rail__name,
    .rail__hint {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .rail__name {
      font: var(--mat-sys-title-small);
    }

    .rail__hint {
      font: var(--mat-sys-body-small);
      color: var(--app-on-nav-variant);
    }

    .shell__toolbar {
      border-block-end: 1px solid var(--mat-sys-outline-variant);
    }

    .shell__title {
      font: var(--mat-sys-title-medium);
    }

    .shell__content {
      display: flex;
      flex-direction: column;
      block-size: 100dvh;
      background-color: var(--app-canvas);
    }

    .shell__main {
      flex-grow: 1;
      min-block-size: 0;
      overflow: auto;
    }
  `,
})
export class ShellComponent {
  private readonly auth = inject(AuthService);
  private readonly breakpoints = inject(BreakpointObserver);

  protected readonly user = this.auth.currentUser;
  protected readonly initials = computed(() =>
    initials(this.user()?.name ?? this.user()?.email ?? ''),
  );
  protected readonly secondLine = computed(() => {
    const user = this.user();
    return user?.name && user.name !== user.email ? user.email : null;
  });
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
