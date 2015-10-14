'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.authentication', [
  require('./authentication.service.js'),
  require('../config/settings.js'),
  require('./authentication.initializer.service.js'),
  require('./authentication.interceptor.service.js')
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
