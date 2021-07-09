import { module } from 'angular';

import { AuthenticationInitializer } from './AuthenticationInitializer';
import { AUTHENTICATION_INTERCEPTOR_SERVICE } from './authentication.interceptor.service';
import { SETTINGS } from '../config/settings';
import { SchedulerFactory } from '../scheduler/SchedulerFactory';

export const AUTHENTICATION_MODULE = 'spinnaker.authentication';
module(AUTHENTICATION_MODULE, [AUTHENTICATION_INTERCEPTOR_SERVICE])
  .config([
    '$httpProvider',
    function ($httpProvider: ng.IHttpProvider) {
      $httpProvider.interceptors.push('gateRequestInterceptor');
    },
  ])
  .factory('gateRequestInterceptor', function () {
    return {
      request(config: ng.IRequestConfig) {
        if (config.url.indexOf(SETTINGS.gateUrl) === 0) {
          config.withCredentials = true;
        }
        return config;
      },
    };
  })
  .run(function () {
    if (SETTINGS.authEnabled) {
      // schedule deck to re-authenticate every 10 min.
      SchedulerFactory.createScheduler(SETTINGS.authTtl || 600000).subscribe(() =>
        AuthenticationInitializer.reauthenticateUser(),
      );
      AuthenticationInitializer.authenticateUser();
    }
  });
