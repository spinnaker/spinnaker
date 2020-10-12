import { AUTHENTICATION_INTERCEPTOR_SERVICE } from './authentication.interceptor.service';
import { AuthenticationInitializer } from './AuthenticationInitializer';
import { SchedulerFactory } from 'core/scheduler/SchedulerFactory';
import { SETTINGS } from 'core/config/settings';
import { CORE_AUTHENTICATION_USERMENU_USERMENU_MODULE } from './userMenu/userMenu.module';

import { module } from 'angular';

export const AUTHENTICATION_MODULE = 'spinnaker.authentication';
module(AUTHENTICATION_MODULE, [AUTHENTICATION_INTERCEPTOR_SERVICE, CORE_AUTHENTICATION_USERMENU_USERMENU_MODULE])
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
