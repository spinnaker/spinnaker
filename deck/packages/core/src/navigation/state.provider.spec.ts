import { StateConfigProvider } from './state.provider';
import { getRootStateRegistrationsForTests, resetRootStateRegistrationsForTests } from './rootState.registration';

describe('StateConfigProvider', () => {
  let originalRegistrations: ReturnType<typeof getRootStateRegistrationsForTests>;

  beforeEach(() => {
    originalRegistrations = getRootStateRegistrationsForTests();
    resetRootStateRegistrationsForTests();
  });

  afterEach(() => resetRootStateRegistrationsForTests(originalRegistrations));

  function createProvider() {
    const urlRouter = { when: jasmine.createSpy('when') };
    const stateHelper = { setNestedState: jasmine.createSpy('setNestedState') };

    return {
      provider: new StateConfigProvider(urlRouter as any, stateHelper as any, {} as any),
      stateHelper,
      urlRouter,
    };
  }

  it('builds dynamic filter params from model and explicit param names', () => {
    const { provider } = createProvider();

    expect(
      provider.buildDynamicParams([
        { model: 'account' } as any,
        { model: 'provider', param: 'cloudProvider', type: 'query', array: true } as any,
      ]),
    ).toEqual({
      account: { type: 'string', dynamic: true },
      cloudProvider: { type: 'query', dynamic: true, array: true },
    });
  });

  it('builds query strings from model and explicit param names', () => {
    const { provider } = createProvider();

    expect(
      provider.paramsToQuery([{ model: 'account' } as any, { model: 'provider', param: 'cloudProvider' } as any]),
    ).toBe('account&cloudProvider');
  });

  it('deduplicates root state children by name before registering nested states', () => {
    const { provider, stateHelper } = createProvider();

    provider.addToRootState({ name: 'applications', url: '/applications' });
    provider.addToRootState({ name: 'applications', url: '/duplicate' });

    const rootState = stateHelper.setNestedState.calls.mostRecent().args[0];
    expect(rootState.children).toEqual([{ name: 'applications', url: '/applications' }]);
  });

  it('registers rewrite rules on the direct URL router', () => {
    const { provider, urlRouter } = createProvider();
    const replacement = () => '/applications';

    provider.addRewriteRule('/apps', replacement);

    expect(urlRouter.when).toHaveBeenCalledWith('/apps', replacement);
  });
});
