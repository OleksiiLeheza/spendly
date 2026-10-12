import { Routes } from '@angular/router';
import { adminGuard } from './core/auth/admin.guard';
import { authGuard } from './core/auth/auth.guard';
import { onboardingGuard } from './features/onboarding/feature/onboarding.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/feature/login.component').then((m) => m.LoginComponent),
    title: 'titles.signIn',
  },
  {
    path: 'callback',
    loadComponent: () =>
      import('./features/auth/feature/callback.component').then((m) => m.CallbackComponent),
    title: 'titles.signingIn',
  },
  {
    path: 'onboarding/account',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/onboarding/feature/onboarding-account.component').then(
        (m) => m.OnboardingAccountComponent,
      ),
    title: 'titles.onboarding',
  },
  {
    path: 'onboarding/categories',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/onboarding/feature/onboarding-categories.component').then(
        (m) => m.OnboardingCategoriesComponent,
      ),
    title: 'titles.onboarding',
  },
  {
    path: '',
    loadComponent: () => import('./layout/shell.component').then((m) => m.ShellComponent),
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        canActivate: [onboardingGuard],
        loadComponent: () =>
          import('./features/dashboard/feature/dashboard.component').then(
            (m) => m.DashboardComponent,
          ),
        title: 'titles.overview',
      },
      {
        path: 'accounts',
        loadComponent: () =>
          import('./features/accounts/feature/accounts.component').then((m) => m.AccountsComponent),
        title: 'titles.accounts',
      },
      {
        path: 'transactions',
        loadComponent: () =>
          import('./features/transactions/feature/transactions.component').then(
            (m) => m.TransactionsComponent,
          ),
        title: 'titles.transactions',
      },
      {
        path: 'transactions/:transactionId',
        loadComponent: () =>
          import('./features/transactions/feature/transaction-detail.component').then(
            (m) => m.TransactionDetailComponent,
          ),
        title: 'titles.transaction',
      },
      {
        path: 'categories',
        loadComponent: () =>
          import('./features/categories/feature/categories.component').then(
            (m) => m.CategoriesComponent,
          ),
        title: 'titles.categories',
      },
      {
        path: 'budgets',
        loadComponent: () =>
          import('./features/budgets/feature/budgets.component').then((m) => m.BudgetsComponent),
        title: 'titles.budgets',
      },
      {
        path: 'recurring',
        loadComponent: () =>
          import('./features/recurring/feature/recurring.component').then(
            (m) => m.RecurringComponent,
          ),
        title: 'titles.recurring',
      },
      {
        path: 'goals',
        loadComponent: () =>
          import('./features/goals/feature/goals.component').then((m) => m.GoalsComponent),
        title: 'titles.goals',
      },
      {
        path: 'goals/:goalId',
        loadComponent: () =>
          import('./features/goals/feature/goal-detail.component').then(
            (m) => m.GoalDetailComponent,
          ),
        title: 'titles.goal',
      },
      {
        path: 'shared-expenses',
        loadComponent: () =>
          import('./features/shared-expenses/feature/groups.component').then(
            (m) => m.GroupsComponent,
          ),
        title: 'titles.shared',
      },
      {
        path: 'shared-expenses/:groupId',
        loadComponent: () =>
          import('./features/shared-expenses/feature/group-detail.component').then(
            (m) => m.GroupDetailComponent,
          ),
        title: 'titles.group',
      },
      {
        path: 'invitations/:token',
        loadComponent: () =>
          import('./features/shared-expenses/feature/invitation.component').then(
            (m) => m.InvitationComponent,
          ),
        title: 'titles.invitation',
      },
      {
        path: 'insights',
        loadComponent: () =>
          import('./features/insights/feature/insights.component').then((m) => m.InsightsComponent),
        title: 'titles.insights',
      },
      {
        path: 'notifications',
        loadComponent: () =>
          import('./features/notifications/feature/notifications.component').then(
            (m) => m.NotificationsComponent,
          ),
        title: 'titles.notifications',
      },
      {
        path: 'notifications/settings',
        loadComponent: () =>
          import('./features/notifications/feature/notification-settings.component').then(
            (m) => m.NotificationSettingsComponent,
          ),
        title: 'titles.notificationSettings',
      },
      {
        path: 'settings',
        loadComponent: () =>
          import('./features/settings/feature/settings.component').then((m) => m.SettingsComponent),
        title: 'titles.settings',
      },
      {
        path: 'admin/dead-letters',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./features/dead-letters/feature/dead-letters.component').then(
            (m) => m.DeadLettersComponent,
          ),
        title: 'titles.deadLetters',
      },
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
    ],
  },
  { path: '**', redirectTo: '' },
];
