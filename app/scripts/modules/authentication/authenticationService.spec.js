'use strict';

describe('authenticationService', function() {

  var $http, settings;
  
  beforeEach(
    window.module(
      require('./authenticationService.js')
    )
  );

  beforeEach(window.inject(function(authenticationService, $httpBackend, _settings_) {
    this.authenticationService = authenticationService;
    $http = $httpBackend;
    settings = _settings_;
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

  describe('authentication', function () {

    it('fires events and sets user', function () {
      $http.expectGET(settings.gateUrl + '/auth/info').respond(200, {email: 'foo@bar.com'});
      var firedEvents = 0;
      this.authenticationService.onAuthentication(function() {
        firedEvents++;
      });
      this.authenticationService.onAuthentication(function() {
        firedEvents++;
      });
      this.authenticationService.authenticateUser();


      $http.flush();
      expect(this.authenticationService.getAuthenticatedUser().name).toBe('foo@bar.com');
      expect(firedEvents).toBe(2);
    });
  });

});
