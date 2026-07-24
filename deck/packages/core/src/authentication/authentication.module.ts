import { AuthenticationInitializer } from './AuthenticationInitializer';
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
}
