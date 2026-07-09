import type { INestedState } from '../navigation/state.provider';
import type { ApplicationStateRegistration } from './applicationState.registration';

import { ApplicationStateProvider } from './application.state.provider';
import {
  applyApplicationStateRegistrations,
  getApplicationStateRegistrationsForTests,
  registerApplicationState,
  resetApplicationStateRegistrationsForTests,
} from './applicationState.registration';

describe('applicationState registration', () => {
  let originalRegistrations: ApplicationStateRegistration[];

  function createProvider(): ApplicationStateProvider {
    return new ApplicationStateProvider({
      setStates: jasmine.createSpy('setStates'),
    } as any);
  }

  beforeEach(() => {
    originalRegistrations = getApplicationStateRegistrationsForTests();
    resetApplicationStateRegistrationsForTests();
  });

  afterEach(() => resetApplicationStateRegistrationsForTests(originalRegistrations));

  it('applies queued registrations when the application state provider is created', () => {
    const detailState: INestedState = { name: 'queuedDetail' };
    const registration = jasmine.createSpy('registration').and.callFake((provider: ApplicationStateProvider) => {
      provider.addInsightDetailState(detailState);
    });

    registerApplicationState(registration);

    const provider = createProvider();
    const insightState: INestedState = { name: 'clusters' };
    provider.addInsightState(insightState);

    expect(registration).toHaveBeenCalledTimes(1);
    expect(insightState.children).toEqual([detailState]);
  });

  it('applies late registrations immediately to the active application state provider', () => {
    const provider = createProvider();
    const childState: INestedState = { name: 'lateChild' };
    const registration = jasmine.createSpy('registration').and.callFake((activeProvider: ApplicationStateProvider) => {
      activeProvider.addChildState(childState);
    });
    const addChildState = spyOn(provider, 'addChildState').and.callThrough();

    registerApplicationState(registration);
    applyApplicationStateRegistrations(provider);

    expect(registration).toHaveBeenCalledTimes(1);
    expect(addChildState).toHaveBeenCalledTimes(1);
    expect(addChildState).toHaveBeenCalledWith(childState);
  });

  it('does not replay queued registrations for the same application state provider', () => {
    const registration = jasmine.createSpy('registration');

    registerApplicationState(registration);

    const provider = createProvider();
    applyApplicationStateRegistrations(provider);

    expect(registration).toHaveBeenCalledTimes(1);
  });
});
