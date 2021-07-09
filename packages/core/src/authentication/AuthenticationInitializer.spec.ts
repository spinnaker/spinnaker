import { mock } from 'angular';

import { IDeckRootScope } from '../domain';
import { AuthenticationInitializer } from './AuthenticationInitializer';
import { AuthenticationService } from './AuthenticationService';
import { AUTHENTICATION_MODULE } from './authentication.module';
import { SETTINGS } from '../config/settings';

declare const window: any;
describe('authenticationProvider: application startup', function () {
  beforeEach(() => (SETTINGS.authEnabled = true));
  beforeEach(() => (window.spinnakerSettings.authEnabled = true));
  beforeEach(() => AuthenticationService.reset());
  beforeEach(mock.module(AUTHENTICATION_MODULE));

  let loginRedirect: any;
  beforeAll(() => {
    loginRedirect = AuthenticationInitializer.loginRedirect;
    AuthenticationInitializer.loginRedirect = (): any => undefined;
  });
  afterAll(() => (AuthenticationInitializer.loginRedirect = loginRedirect));

  let $timeout: ng.ITimeoutService, $httpBackend: ng.IHttpBackendService, $rootScope: IDeckRootScope;

  beforeEach(
    mock.inject(
      (_$timeout_: ng.ITimeoutService, _$httpBackend_: ng.IHttpBackendService, _$rootScope_: IDeckRootScope) => {
        $timeout = _$timeout_;
        $httpBackend = _$httpBackend_;
        $rootScope = _$rootScope_;
      },
    ),
  );

  afterEach(SETTINGS.resetToOriginal);

  describe('authenticateUser', () => {
    it('requests authentication from gate, then sets authentication name field', function () {
      $httpBackend.whenGET(SETTINGS.authEndpoint).respond(200, { username: 'joe!' });
      $timeout.flush();
      $httpBackend.flush();

      expect($rootScope.authenticating).toBe(false);
      expect(AuthenticationService.getAuthenticatedUser().name).toBe('joe!');
      expect(AuthenticationService.getAuthenticatedUser().authenticated).toBe(true);
    });

    it('requests authentication from gate, then opens modal and redirects on 401', function () {
      $httpBackend.whenGET(SETTINGS.authEndpoint).respond(401, null, { 'X-AUTH-REDIRECT-URL': '/authUp' });
      $rootScope.$digest();
      $httpBackend.flush();

      expect($rootScope.authenticating).toBe(true);
      expect(AuthenticationService.getAuthenticatedUser().name).toBe('[anonymous]');
      expect(AuthenticationService.getAuthenticatedUser().authenticated).toBe(false);
    });
  });
});
