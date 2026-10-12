import { computed, inject } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap, tap } from 'rxjs';
import { catchError, of } from 'rxjs';
import { UserResponse, UsersApi } from '../../../core/api/user';

interface ProfileState {
  profile: UserResponse | null;
  loading: boolean;
  error: string | null;
}

const initialState: ProfileState = { profile: null, loading: false, error: null };

/**
 * The single owner of the caller's profile (CLAUDE.md 9.3.6). Components read these signals;
 * nothing copies the value into a second signal.
 */
export const ProfileStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withComputed(({ profile, loading, error }) => ({
    isEmpty: computed(() => !loading() && !error() && profile() === null),
  })),
  withMethods((store, api = inject(UsersApi)) => ({
    load: rxMethod<void>(
      pipe(
        tap(() => patchState(store, { loading: true, error: null })),
        switchMap(() =>
          api.me().pipe(
            tap((profile) => patchState(store, { profile, loading: false })),
            catchError((err: { error?: { detail?: string }; status?: number }) => {
              patchState(store, {
                loading: false,
                error:
                  err.error?.detail ?? `Could not load your profile (HTTP ${err.status ?? '?'})`,
              });
              return of(null);
            }),
          ),
        ),
      ),
    ),
  })),
);
