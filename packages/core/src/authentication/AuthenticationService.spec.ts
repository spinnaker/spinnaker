import { AuthenticationService, IUser } from './AuthenticationService';

describe('AuthenticationService', function () {
  beforeEach(() => AuthenticationService.reset());

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
      AuthenticationService.onAuthentication(() => firedEvents++);
      AuthenticationService.onAuthentication(() => firedEvents++);
      AuthenticationService.setAuthenticatedUser({ name: 'foo@bar.com', authenticated: false });
      expect(AuthenticationService.getAuthenticatedUser().name).toBe('foo@bar.com');
      expect(firedEvents).toBe(2);
    });
  });
});
