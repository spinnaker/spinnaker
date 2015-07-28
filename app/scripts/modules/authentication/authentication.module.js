'use strict';

angular.module('spinnaker.authentication', [
  'ui.bootstrap',
  'spinnaker.authentication.service',
  'spinnaker.authentication.interceptor.service',
  'spinnaker.authentication.initializer.service',
  'spinnaker.authentication.directive',
  'spinnaker.settings',
])
  .config(function ($httpProvider) {
    $httpProvider.interceptors.push('gateRequestInterceptor');
  })
  .run(function (authenticationInitializer, settings) {
    if(settings.authEnabled) {
      authenticationInitializer.authenticateUser();
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
