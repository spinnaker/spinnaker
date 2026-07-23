import { module } from 'angular';

import { AuthenticationInitializer } from './AuthenticationInitializer';
import { AngularServices } from '../angular/services';
import { AUTHENTICATION_INTERCEPTOR_SERVICE } from './authentication.interceptor.service';
import { SETTINGS } from '../config/settings';
import type { IScheduler } from '../scheduler/SchedulerFactory';
import { SchedulerFactory } from '../scheduler/SchedulerFactory';

export const AUTHENTICATION_MODULE = 'spinnaker.authentication';

let authenticationScheduler: IScheduler = null;
let authenticationInFlight: Promise<boolean> = null;
let authenticationGeneration = 0;

export function initializeAuthentication(): Promise<boolean> {
  if (!SETTINGS.authEnabled) {
    return Promise.resolve(true);
  }

  if (!authenticationScheduler) {
    const authTtl = Number.isFinite(SETTINGS.authTtl) && SETTINGS.authTtl > 0 ? SETTINGS.authTtl : 600000;
    authenticationScheduler = SchedulerFactory.createScheduler(authTtl);
    authenticationScheduler.subscribe(() => AuthenticationInitializer.reauthenticateUser());
  }

  if (!authenticationInFlight) {
    const generation = authenticationGeneration;
    const authentication = AuthenticationInitializer.authenticateUser(() => generation === authenticationGeneration);
    const trackedAuthentication = authentication.finally(() => {
      if (generation === authenticationGeneration && authenticationInFlight === trackedAuthentication) {
        authenticationInFlight = null;
      }
    });
    authenticationInFlight = trackedAuthentication;
  }

  return authenticationInFlight;
}

export function resetAuthenticationRuntime(): void {
  authenticationGeneration++;
  authenticationInFlight = null;
  authenticationScheduler?.unsubscribe();
  authenticationScheduler = null;
  (AngularServices.$rootScope as any).authenticating = false;
}

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
    void initializeAuthentication().catch((error) => {
      AngularServices.$log.error('Failed to initialize authentication', error);
    });
  });
