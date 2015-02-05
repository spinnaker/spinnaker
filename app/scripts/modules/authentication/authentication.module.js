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
  .run(function ($timeout, authenticationService, settings) {
    // timeout allows initial rerouting to occur; otherwise, we potentially immediately redirect on page load,
    // which closes the "Authenticating..." modal
    if(settings.authEnabled) {
      $timeout(authenticationService.authenticateUser);
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
