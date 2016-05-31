'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.authentication', [
  require('./authentication.service.js'),
  require('../config/settings.js'),
  require('./authentication.initializer.service.js'),
  require('./authentication.interceptor.service.js'),
  require('./userMenu/userMenu.module.js'),
  require('../scheduler/scheduler.factory.js')
])
  .config(function ($httpProvider) {
    $httpProvider.interceptors.push('gateRequestInterceptor');
  })
  .run(function (schedulerFactory, authenticationInitializer, settings) {
    if (settings.authEnabled) {
      // schedule deck to re-authenticate every 10 min.
      schedulerFactory.createScheduler(600000).subscribe(authenticationInitializer.reauthenticateUser);
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
