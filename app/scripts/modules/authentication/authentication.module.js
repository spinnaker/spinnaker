'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.authentication', [
  require('../authentication/authenticationService.js'),
  require('../authentication/authenticatedUserDirective.js'),
  require('../../settings/settings.js'),
  'spinnaker.authentication.interceptor.service',
  'spinnaker.authentication.initializer.service',
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
  })
  .name;
