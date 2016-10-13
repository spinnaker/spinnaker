import {AUTHENTICATION_SERVICE, AuthenticationService} from './authentication.service';

describe('authenticationService', function() {

  beforeEach(angular.mock.module(AUTHENTICATION_SERVICE));

  beforeEach(angular.mock.inject(function(authenticationService: AuthenticationService) {
    this.authenticationService = authenticationService;
  }));

  describe('setAuthenticatedUser', function() {
    it('sets name, authenticated flag', function() {
      let user = this.authenticationService.getAuthenticatedUser();
      expect(user.name).toBe('[anonymous]');
      expect(user.authenticated).toBe(false);

      this.authenticationService.setAuthenticatedUser('kato@example.com');
      user = this.authenticationService.getAuthenticatedUser();
      expect(user.name).toBe('kato@example.com');
      expect(user.authenticated).toBe(true);
    });

    it('disregards falsy values', function() {
      const user = this.authenticationService.getAuthenticatedUser();

      expect(user.name).toBe('[anonymous]');
      expect(user.authenticated).toBe(false);

      this.authenticationService.setAuthenticatedUser(null);
      expect(user.name).toBe('[anonymous]');
      expect(user.authenticated).toBe(false);

      this.authenticationService.setAuthenticatedUser(false);
      expect(user.name).toBe('[anonymous]');
      expect(user.authenticated).toBe(false);

      this.authenticationService.setAuthenticatedUser('');
      expect(user.name).toBe('[anonymous]');
      expect(user.authenticated).toBe(false);
    });
  });

  describe('authentication', function () {

    it('fires events and sets user', function () {
      let firedEvents = 0;
      this.authenticationService.onAuthentication(() => firedEvents++);
      this.authenticationService.onAuthentication(() => firedEvents++);
      this.authenticationService.setAuthenticatedUser('foo@bar.com');
      expect(this.authenticationService.getAuthenticatedUser().name).toBe('foo@bar.com');
      expect(firedEvents).toBe(2);
    });
  });
});
