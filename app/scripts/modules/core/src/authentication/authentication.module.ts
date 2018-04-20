import { AUTHENTICATION_INTERCEPTOR_SERVICE } from './authentication.interceptor.service';
import { AuthenticationInitializer } from './AuthenticationInitializer';
import { SCHEDULER_FACTORY, SchedulerFactory } from 'core/scheduler/scheduler.factory';
import { SETTINGS } from 'core/config/settings';

const angular = require('angular');

export const AUTHENTICATION_MODULE = 'spinnaker.authentication';
angular
  .module(AUTHENTICATION_MODULE, [
    AUTHENTICATION_INTERCEPTOR_SERVICE,
    require('./userMenu/userMenu.module.js').name,
    SCHEDULER_FACTORY,
  ])
  .config(function($httpProvider: ng.IHttpProvider) {
    $httpProvider.interceptors.push('gateRequestInterceptor');
  })
  .factory('gateRequestInterceptor', function() {
    return {
      request(config: ng.IRequestConfig) {
        if (config.url.indexOf(SETTINGS.gateUrl) === 0) {
          config.withCredentials = true;
        }
        return config;
      },
    };
  })
  .run(function(schedulerFactory: SchedulerFactory) {
    if (SETTINGS.authEnabled) {
      // schedule deck to re-authenticate every 10 min.
      schedulerFactory
        .createScheduler(SETTINGS.authTtl || 600000)
        .subscribe(() => AuthenticationInitializer.reauthenticateUser());
      AuthenticationInitializer.authenticateUser();
    }
  });
