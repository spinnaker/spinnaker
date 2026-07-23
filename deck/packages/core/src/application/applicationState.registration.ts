import type { ApplicationStateProvider } from './application.state.provider';
import type { StateConfigProvider } from '../navigation/state.provider';

export type ApplicationStateRegistration = (
  provider: ApplicationStateProvider,
  stateConfigProvider: StateConfigProvider,
) => void;

const registrations: ApplicationStateRegistration[] = [];
let appliedProvider: ApplicationStateProvider | null = null;

export function registerApplicationState(registration: ApplicationStateRegistration): void {
  registrations.push(registration);

  if (appliedProvider) {
    registration(appliedProvider, appliedProvider.stateConfigProvider);
  }
}

export function applyApplicationStateRegistrations(provider: ApplicationStateProvider): void {
  if (appliedProvider === provider) {
    return;
  }

  appliedProvider = provider;
  registrations.forEach((registration) => registration(provider, provider.stateConfigProvider));
}

export function getActiveApplicationStateProvider(): ApplicationStateProvider | null {
  return appliedProvider;
}

export function getApplicationStateRegistrationsForTests(): ApplicationStateRegistration[] {
  return registrations.slice();
}

export function resetApplicationStateRegistrationsForTests(
  registrationsForTests: ApplicationStateRegistration[] = [],
): void {
  registrations.length = 0;
  registrations.push(...registrationsForTests);
  appliedProvider = null;
}
