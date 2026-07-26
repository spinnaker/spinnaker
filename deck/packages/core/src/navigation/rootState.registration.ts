import type { StateConfigProvider } from './state.provider';

export type RootStateRegistration = (provider: StateConfigProvider) => void;

const registrations: RootStateRegistration[] = [];
let appliedProvider: StateConfigProvider | null = null;

export function registerRootState(registration: RootStateRegistration): void {
  registrations.push(registration);

  if (appliedProvider) {
    registration(appliedProvider);
  }
}

export function applyRootStateRegistrations(provider: StateConfigProvider): void {
  if (appliedProvider === provider) {
    return;
  }

  appliedProvider = provider;
  registrations.forEach((registration) => registration(provider));
}

export function getRootStateRegistrationsForTests(): RootStateRegistration[] {
  return registrations.slice();
}

export function resetRootStateRegistrationsForTests(registrationsForTests: RootStateRegistration[] = []): void {
  registrations.length = 0;
  registrations.push(...registrationsForTests);
  appliedProvider = null;
}
