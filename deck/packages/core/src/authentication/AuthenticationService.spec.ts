import type { IUser } from './AuthenticationService';
import { AuthenticationService } from './AuthenticationService';

describe('AuthenticationService', function () {
  const authenticationUnsubscribes: Array<() => void> = [];

  beforeEach(() => AuthenticationService.reset());
  afterEach(() => authenticationUnsubscribes.splice(0).forEach((unsubscribe) => unsubscribe()));

  describe('setAuthenticatedUser', function () {
    it('sets name, authenticated flag', function () {
      let user: IUser = AuthenticationService.getAuthenticatedUser();
      expect(user.name).toBe('[anonymous]');
      expect(user.authenticated).toBe(false);

      AuthenticationService.setAuthenticatedUser({ name: 'kato@example.com', authenticated: false });
      user = AuthenticationService.getAuthenticatedUser();
      expect(user.name).toBe('kato@example.com');
      expect(user.authenticated).toBe(true);
    });

    it('defaults canMintApiTokens to false', function () {
      let user: IUser = AuthenticationService.getAuthenticatedUser();
      expect(user.canMintApiTokens).toBe(false);

      AuthenticationService.setAuthenticatedUser({ name: 'kato@example.com', authenticated: false });
      user = AuthenticationService.getAuthenticatedUser();
      expect(user.canMintApiTokens).toBe(false);
    });

    it('stores canMintApiTokens when provided', function () {
      AuthenticationService.setAuthenticatedUser({
        name: 'kato@example.com',
        authenticated: false,
        canMintApiTokens: true,
      });
      expect(AuthenticationService.getAuthenticatedUser().canMintApiTokens).toBe(true);
    });

    it('disregards falsy values', function () {
      const user: IUser = AuthenticationService.getAuthenticatedUser();

      expect(user.name).toBe('[anonymous]');
      expect(user.authenticated).toBe(false);

      AuthenticationService.setAuthenticatedUser(null);
      expect(user.name).toBe('[anonymous]');
      expect(user.authenticated).toBe(false);

      AuthenticationService.setAuthenticatedUser({ name: '', authenticated: false });
      expect(user.name).toBe('[anonymous]');
      expect(user.authenticated).toBe(false);
    });
  });

  describe('authentication', function () {
    it('fires events and sets user', function () {
      let firedEvents = 0;
      authenticationUnsubscribes.push(
        AuthenticationService.onAuthentication(() => firedEvents++),
        AuthenticationService.onAuthentication(() => firedEvents++),
      );
      AuthenticationService.setAuthenticatedUser({ name: 'foo@bar.com', authenticated: false });
      expect(AuthenticationService.getAuthenticatedUser().name).toBe('foo@bar.com');
      expect(firedEvents).toBe(2);
    });

    it('can unsubscribe an authentication event', function () {
      const event = jasmine.createSpy('event');
      const unsubscribe = AuthenticationService.onAuthentication(event);
      authenticationUnsubscribes.push(unsubscribe);

      AuthenticationService.setAuthenticatedUser({ name: 'foo@bar.com', authenticated: false });
      unsubscribe();
      AuthenticationService.setAuthenticatedUser({ name: 'bar@foo.com', authenticated: false });

      expect(event).toHaveBeenCalledTimes(1);
    });

    it('reports a failing event and continues notifying the remaining events', function () {
      const listenerError = new Error('listener failed');
      const reportError = spyOn(console, 'error');
      const nextEvent = jasmine.createSpy('nextEvent');
      authenticationUnsubscribes.push(
        AuthenticationService.onAuthentication(() => {
          throw listenerError;
        }),
        AuthenticationService.onAuthentication(nextEvent),
      );

      expect(() =>
        AuthenticationService.setAuthenticatedUser({ name: 'foo@bar.com', authenticated: false }),
      ).not.toThrow();

      expect(nextEvent).toHaveBeenCalledTimes(1);
      expect(reportError).toHaveBeenCalledOnceWith('Authentication listener failed', listenerError);
    });
  });
});
