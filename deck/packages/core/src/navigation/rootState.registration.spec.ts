import type { INestedState } from './state.provider';
import { StateConfigProvider } from './state.provider';
import {
  applyRootStateRegistrations,
  getRootStateRegistrationsForTests,
  registerRootState,
  resetRootStateRegistrationsForTests,
} from './rootState.registration';

describe('rootState registration', () => {
  let originalRegistrations: Array<(provider: StateConfigProvider) => void>;

  function createProvider(): StateConfigProvider {
    return new StateConfigProvider({} as any, { setNestedState: vi.fn() } as any, {} as any);
  }

  beforeEach(() => {
    originalRegistrations = getRootStateRegistrationsForTests();
    resetRootStateRegistrationsForTests();
  });

  afterEach(() => resetRootStateRegistrationsForTests(originalRegistrations));

  it('applies queued root state registrations when the state config provider is created', () => {
    const rootState: INestedState = { name: 'queuedRootState' };
    const registration = vi.fn().mockImplementation((provider: StateConfigProvider) => {
      provider.addToRootState(rootState);
    });

    registerRootState(registration);

    createProvider();

    expect(registration).toHaveBeenCalledTimes(1);
  });

  it('does not apply queued registrations when the state helper provider is unavailable', () => {
    const registration = vi.fn();

    registerRootState(registration);

    new StateConfigProvider(undefined as any, undefined as any, {} as any);

    expect(registration).not.toHaveBeenCalled();
  });

  it('applies late root state registrations immediately to the active state config provider', () => {
    const provider = createProvider();
    const rootState: INestedState = { name: 'lateRootState' };
    const registration = vi.fn().mockImplementation((activeProvider: StateConfigProvider) => {
      activeProvider.addToRootState(rootState);
    });
    const addToRootState = vi.spyOn(provider, 'addToRootState');

    applyRootStateRegistrations(provider);
    registerRootState(registration);

    expect(registration).toHaveBeenCalledTimes(1);
    expect(addToRootState).toHaveBeenCalledWith(rootState);
  });
});
