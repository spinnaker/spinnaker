import { IQService, IRequestConfig, IRootScopeService, mock } from 'angular';

import { AUTHENTICATION_INTERCEPTOR_SERVICE, AuthenticationInterceptor } from './authentication.interceptor.service';
import { AuthenticationService } from './AuthenticationService';
import { SETTINGS } from '../config/settings';

describe('authenticationInterceptor', function () {
  let interceptor: AuthenticationInterceptor, $rootScope: IRootScopeService;

  beforeEach(mock.module(AUTHENTICATION_INTERCEPTOR_SERVICE));

  beforeEach(
    mock.inject(function (
      _$q_: IQService,
      _$rootScope_: IRootScopeService,
      _authenticationInterceptor_: AuthenticationInterceptor,
    ) {
      $rootScope = _$rootScope_;
      interceptor = _authenticationInterceptor_;
    }),
  );

  describe('non-intercepted requests', function () {
    it('resolves immediately for auth endpoint', function () {
      let resolved: IRequestConfig = null;
      const request: IRequestConfig = { url: SETTINGS.authEndpoint, method: 'GET' };
      interceptor.request(request).then(function (result) {
        resolved = result;
      });
      $rootScope.$digest();
      expect(resolved).toBe(request);
    });

    it('resolves immediately for relative and non-http requests', function () {
      let resolved: IRequestConfig = null;
      const request: IRequestConfig = { url: '/something/relative', method: 'GET' };
      interceptor.request(request).then(function (result) {
        resolved = result;
      });
      $rootScope.$digest();
      expect(resolved.url).toBe(request.url);

      request.url = 'tcp://what.are.you.doing.here';
      interceptor.request(request).then(function (result) {
        resolved = result;
      });
      $rootScope.$digest();
      expect(resolved.url).toBe(request.url);
    });
  });

  describe('intercepted requests', function () {
    it('registers event with authentication service and does not resolve when not authenticated', function () {
      let resolved: IRequestConfig = null;
      const request: IRequestConfig = { url: 'http://some-server.spinnaker.org', method: 'GET' };
      const pendingRequests: Function[] = [];

      spyOn(AuthenticationService, 'getAuthenticatedUser').and.returnValue({ authenticated: false } as any);
      spyOn(AuthenticationService, 'onAuthentication').and.callFake((pendingRequest: Function) =>
        pendingRequests.push(pendingRequest),
      );

      interceptor.request(request).then(function (result) {
        resolved = result;
      });
      $rootScope.$digest();
      expect(resolved).toBe(null);
      expect(pendingRequests.length).toBe(1);

      // simulate authentication event
      pendingRequests[0]();
      $rootScope.$digest();
      expect(resolved).toBe(request);
    });

    it('resolves immediately when authenticated', function () {
      let resolved: IRequestConfig = null;
      const request: IRequestConfig = { url: 'http://some-server.spinnaker.org', method: 'GET' };

      spyOn(AuthenticationService, 'getAuthenticatedUser').and.returnValue({
        authenticated: true,
        lastAuthenticated: new Date().getTime(),
      } as any);

      interceptor.request(request).then(function (result) {
        resolved = result;
      });
      $rootScope.$digest();
      expect(resolved.url).toBe(request.url);
    });
  });
});
