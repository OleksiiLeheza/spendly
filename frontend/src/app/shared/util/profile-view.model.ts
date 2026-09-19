/**
 * What the profile card renders. Not an API interface (those are generated, CLAUDE.md 9.1) -
 * this is the presentation contract, which is why a dumb component may depend on it while
 * it may not reach into core or data-access.
 */
export interface ProfileView {
  readonly id: string;
  readonly email: string;
  readonly displayName: string;
  readonly defaultCurrency: string;
  readonly identityProvider: string | null;
  readonly createdAt: string;
  readonly roles: readonly string[];
}
