import {ReflectiveInjector} from '@angular/core';
import {mock, module} from 'angular';

import {AUTHENTICATION_INTERCEPTOR_SERVICE, AuthenticationInterceptor} from './authentication.interceptor.service';
import {AUTHENTICATION_SERVICE, AuthenticationService} from './authentication.service';
import {SETTINGS} from 'core/config/settings';

describe('authenticationInterceptor', function() {

  let interceptor: AuthenticationInterceptor,
    authenticationService: AuthenticationService,
    $rootScope: ng.IRootScopeService;

  module(AUTHENTICATION_SERVICE, [])
    .factory('injector', function () {
      return ReflectiveInjector.resolveAndCreate([AuthenticationService]);
    })
    .factory('authenticationService', function (injector: ReflectiveInjector) {
      return injector.get(AuthenticationService);
    });

  beforeEach(mock.module(AUTHENTICATION_INTERCEPTOR_SERVICE));

  beforeEach(
    mock.inject(
      function (_$q_: ng.IQService,
                _authenticationService_: AuthenticationService,
                _$rootScope_: ng.IRootScopeService,
                _authenticationInterceptor_: AuthenticationInterceptor) {
        authenticationService = _authenticationService_;
        $rootScope = _$rootScope_;
        interceptor = _authenticationInterceptor_;
      }));

  describe('non-intercepted requests', function() {
    it('resolves immediately for auth endpoint', function() {
      let resolved: ng.IRequestConfig = null;
      const request: ng.IRequestConfig = { url: SETTINGS.authEndpoint, method: 'GET' };
      interceptor.request(request).then(function(result) { resolved = result; });
      $rootScope.$digest();
      expect(resolved).toBe(request);
    });

    it('resolves immediately for relative and non-http requests', function() {
      let resolved: ng.IRequestConfig = null;
      const request: ng.IRequestConfig = { url: '/something/relative', method: 'GET' };
      interceptor.request(request).then(function(result) { resolved = result; });
      $rootScope.$digest();
      expect(resolved.url).toBe(request.url);

      request.url = 'tcp://what.are.you.doing.here';
      interceptor.request(request).then(function(result) { resolved = result; });
      $rootScope.$digest();
      expect(resolved.url).toBe(request.url);
    });
  });

  describe('intercepted requests', function () {

    it('registers event with authentication service and does not resolve when not authenticated', function () {
      let resolved: ng.IRequestConfig = null;
      const request: ng.IRequestConfig = { url: 'http://some-server.spinnaker.org', method: 'GET' };
      const pendingRequests: Function[] = [];

      spyOn(authenticationService, 'getAuthenticatedUser').and.returnValue({ authenticated: false });
      spyOn(authenticationService, 'onAuthentication').and
        .callFake((pendingRequest: Function) => pendingRequests.push(pendingRequest));

      interceptor.request(request).then(function(result) { resolved = result; });
      $rootScope.$digest();
      expect(resolved).toBe(null);
      expect(pendingRequests.length).toBe(1);

      // simulate authentication event
      pendingRequests[0]();
      $rootScope.$digest();
      expect(resolved).toBe(request);
    });

    it('resolves immediately when authenticated', function () {
      let resolved: ng.IRequestConfig = null;
      const request: ng.IRequestConfig = { url: 'http://some-server.spinnaker.org', method: 'GET' };

      spyOn(authenticationService, 'getAuthenticatedUser').and.returnValue({ authenticated: true, lastAuthenticated: new Date().getTime() });

      interceptor.request(request).then(function(result) { resolved = result; });
      $rootScope.$digest();
      expect(resolved.url).toBe(request.url);
    });
  });
});
