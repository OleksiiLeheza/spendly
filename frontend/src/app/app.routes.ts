import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/feature/login.component').then((m) => m.LoginComponent),
    title: 'Sign in · Spendly',
  },
  {
    path: 'callback',
    loadComponent: () =>
      import('./features/auth/feature/callback.component').then((m) => m.CallbackComponent),
    title: 'Signing in · Spendly',
  },
  {
    path: '',
    loadComponent: () => import('./layout/shell.component').then((m) => m.ShellComponent),
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/feature/dashboard.component').then(
            (m) => m.DashboardComponent,
          ),
        title: 'Dashboard · Spendly',
      },
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
    ],
  },
  { path: '**', redirectTo: '' },
];
