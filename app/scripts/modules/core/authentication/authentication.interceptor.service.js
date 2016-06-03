'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.authentication.interceptor.service', [
  require('../config/settings.js'),
  require('./authentication.service.js')
])
  .factory('authenticationInterceptor', function ($q, settings, authenticationService) {

    return {
      request: function (config) {
        var deferred = $q.defer();
        // pass through to authentication endpoint and non-http resources
        if (config.url === settings.authEndpoint || config.url.indexOf('http') !== 0) {
          deferred.resolve(config);
        } else {
          let user = authenticationService.getAuthenticatedUser();
          // only send the request if the user has authenticated within the refresh window for auth calls
          if (user.authenticated && user.lastAuthenticated + (settings.authTtl || 600000) > new Date().getTime()) {
            deferred.resolve(config);
          } else {
            authenticationService.onAuthentication(() => deferred.resolve(config));
          }
        }
        return deferred.promise;
      }
    };
  })
  .config(function ($httpProvider, settings) {
    if (settings.authEnabled) {
      $httpProvider.interceptors.push('authenticationInterceptor');
    }
  });
