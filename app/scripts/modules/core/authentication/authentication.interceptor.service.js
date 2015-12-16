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
          if (authenticationService.getAuthenticatedUser().authenticated) {
            deferred.resolve(config);
          } else {
            authenticationService.onAuthentication(function () {
              deferred.resolve(config);
            });
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
