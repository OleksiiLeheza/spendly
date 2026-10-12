import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { rememberReturnUrl } from './return-url';

/**
 * Sends unauthenticated visitors to the login page instead of a blank protected route, and
 * remembers where they were going so an invitation link still opens the invitation afterwards.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return true;
  }
  rememberReturnUrl(state.url);
  return router.createUrlTree(['/login']);
};
