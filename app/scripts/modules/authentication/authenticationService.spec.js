'use strict';

describe('authenticationService', function() {

  beforeEach(function() {
    loadDeck({
      initializeCache: false,
      generateUrls: true
    })
  });

  beforeEach(inject(function(authenticationService) {
    this.authenticationService = authenticationService;
  }));

  describe('setAuthenticatedUser', function() {
    it('sets name, authenticated flag', function() {
      var user = this.authenticationService.getAuthenticatedUser();

      expect(user.name).toBe('[anonymous]');
      expect(user.authenticated).toBe(false);

      this.authenticationService.setAuthenticatedUser('kato@example.com');
      expect(user.name).toBe('kato@example.com');
      expect(user.authenticated).toBe(true);
    });

    it('disregards falsy values', function() {
      var user = this.authenticationService.getAuthenticatedUser();

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

});
