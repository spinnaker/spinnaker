'use strict';

angular.module('spinnaker.authentication', [
  'ui.bootstrap',
  'spinnaker.authentication.service',
  'spinnaker.authentication.directive',
  'spinnaker.settings',
])
  .config(function ($httpProvider) {
    $httpProvider.interceptors.push('gateRequestInterceptor');
  })
  .run(function (authenticationService, settings) {
    if(settings.authEnabled) {
      authenticationService.authenticateUser();
    }
  })
  .factory('gateRequestInterceptor', function (settings) {
    return {
      request: function (config) {
        if (config.url.indexOf(settings.gateUrl) === 0) {
          config.withCredentials = true;
        }
        return config;
      }
    };
  });
