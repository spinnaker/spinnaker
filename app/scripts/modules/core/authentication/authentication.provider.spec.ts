import {mock} from 'angular';

import {IDeckRootScope} from 'core/domain';
import {RedirectService} from './redirect.service';
import {AuthenticationService} from './authentication.service';
import {AUTHENTICATION} from './authentication.module';
import {SETTINGS} from 'core/config/settings';

declare const window: any;
describe('authenticationProvider: application startup', function () {
  beforeEach(function () {
    SETTINGS.authEnabled = true;
  });

  beforeEach(function () {
    window.spinnakerSettings.authEnabled = true;
  });

  beforeEach(mock.module(AUTHENTICATION));

  let authenticationService: AuthenticationService,
    $timeout: ng.ITimeoutService,
    $http: ng.IHttpBackendService,
    redirectService: RedirectService,
    $location: ng.ILocationService,
    $rootScope: IDeckRootScope;

  beforeEach(
    mock.inject(
      (_authenticationService_: AuthenticationService,
       _$timeout_: ng.ITimeoutService,
       _$httpBackend_: ng.IHttpBackendService,
       _redirectService_: RedirectService,
       _$location_: ng.ILocationService,
       _$rootScope_: IDeckRootScope) => {

        authenticationService = _authenticationService_;
        $timeout = _$timeout_;
        $http = _$httpBackend_;


        redirectService = _redirectService_;
        $location = _$location_;
        $rootScope = _$rootScope_;
      }));

  afterEach(SETTINGS.resetToOriginal);

  describe('authenticateUser', () => {
    it('requests authentication from gate, then sets authentication name field', function () {

      $http.whenGET(SETTINGS.authEndpoint).respond(200, {username: 'joe!'});
      $timeout.flush();
      $http.flush();

      expect($rootScope.authenticating).toBe(false);
      expect(authenticationService.getAuthenticatedUser().name).toBe('joe!');
      expect(authenticationService.getAuthenticatedUser().authenticated).toBe(true);
    });

    it('requests authentication from gate, then opens modal and redirects on 401', function () {
      let redirectUrl = 'abc';
      spyOn(redirectService, 'redirect').and.callFake((url: string) => redirectUrl = url);
      $http.whenGET(SETTINGS.authEndpoint).respond(401, null, {'X-AUTH-REDIRECT-URL': '/authUp'});
      $rootScope.$digest();
      $http.flush();

      const callback = encodeURIComponent($location.absUrl());
      expect($rootScope.authenticating).toBe(true);
      expect(authenticationService.getAuthenticatedUser().name).toBe('[anonymous]');
      expect(authenticationService.getAuthenticatedUser().authenticated).toBe(false);
      expect(redirectUrl).toBe(`${SETTINGS.gateUrl}/auth/redirect?to=${callback}`);
    });
  });
});
