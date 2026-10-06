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
      setStates: vi.fn(),
    } as any);
  }

  beforeEach(() => {
    originalRegistrations = getApplicationStateRegistrationsForTests();
    resetApplicationStateRegistrationsForTests();
  });

  afterEach(() => resetApplicationStateRegistrationsForTests(originalRegistrations));

  it('applies queued registrations when the application state provider is created', () => {
    const detailState: INestedState = { name: 'queuedDetail' };
    const registration = vi.fn().mockImplementation((provider: ApplicationStateProvider) => {
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
    const registration = vi.fn().mockImplementation((activeProvider: ApplicationStateProvider) => {
      activeProvider.addChildState(childState);
    });
    const addChildState = vi.spyOn(provider, 'addChildState');

    registerApplicationState(registration);
    applyApplicationStateRegistrations(provider);

    expect(registration).toHaveBeenCalledTimes(1);
    expect(addChildState).toHaveBeenCalledTimes(1);
    expect(addChildState).toHaveBeenCalledWith(childState);
  });

  it('passes the state config provider to registrations', () => {
    const stateConfigProvider = { setStates: vi.fn() } as any;
    const registration = vi.fn();

    registerApplicationState(registration);

    const provider = new ApplicationStateProvider(stateConfigProvider);

    expect(registration).toHaveBeenCalledWith(provider, stateConfigProvider);
  });

  it('does not replay queued registrations for the same application state provider', () => {
    const registration = vi.fn();

    registerApplicationState(registration);

    const provider = createProvider();
    applyApplicationStateRegistrations(provider);

    expect(registration).toHaveBeenCalledTimes(1);
  });

  it('does not add duplicate insight detail states by name', () => {
    const provider = createProvider();
    const detailState: INestedState = { name: 'serverGroupManager' };
    const insightState: INestedState = { name: 'clusters' };

    provider.addInsightDetailState(detailState);
    provider.addInsightDetailState(detailState);
    provider.addInsightState(insightState);

    expect(insightState.children).toEqual([detailState]);
  });
});
