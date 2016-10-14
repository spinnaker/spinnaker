'use strict';

import {AUTHENTICATION_INTERCEPTOR_SERVICE} from './authentication.interceptor.service';
import {AUTHENTICATION_INITIALIZER_SERVICE, AuthenticationInitializer} from './authentication.initializer.service';
import {REDIRECT_SERVICE} from './redirect.service';
import {AUTHENTICATION_SERVICE} from './authentication.service';

let angular = require('angular');

export const AUTHENTICATION = 'spinnaker.authentication';
angular.module(AUTHENTICATION, [
  AUTHENTICATION_SERVICE,
  require('../config/settings.js'),
  REDIRECT_SERVICE,
  AUTHENTICATION_INITIALIZER_SERVICE,
  AUTHENTICATION_INTERCEPTOR_SERVICE,
  require('./userMenu/userMenu.module.js'),
  require('../scheduler/scheduler.factory.js')
])
  .config(function ($httpProvider: ng.IHttpProvider) {
    $httpProvider.interceptors.push('gateRequestInterceptor');
  })
  .factory('gateRequestInterceptor', function (settings: any) {
    return {
      request: function (config: ng.IRequestConfig) {
        if (config.url.indexOf(settings.gateUrl) === 0) {
          config.withCredentials = true;
        }
        return config;
      }
    };
  })
  .run(function (schedulerFactory: any,
                 authenticationInitializer: AuthenticationInitializer,
                 settings: any) {
    if (settings.authEnabled) {
      // schedule deck to re-authenticate every 10 min.
      schedulerFactory.createScheduler(settings.authTtl || 600000)
        .subscribe(() => authenticationInitializer.reauthenticateUser());
      authenticationInitializer.authenticateUser();
    }
  });
