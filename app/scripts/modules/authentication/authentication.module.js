'use strict';

angular.module('deckApp.authentication', [
  'ui.bootstrap',
  'deckApp.authentication.service',
  'deckApp.authentication.directive',
  'deckApp.settings',
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
