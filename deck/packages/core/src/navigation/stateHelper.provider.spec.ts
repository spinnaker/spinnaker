import { StateHelper } from './stateHelper.provider';

describe('StateHelper', () => {
  it('registers nested states with fully qualified child names', () => {
    const stateRegistry = { register: jasmine.createSpy('register') };
    const helper = new StateHelper(stateRegistry as any);

    helper.setNestedState({
      name: 'home',
      children: [{ name: 'applications', children: [{ name: 'application' }] }],
    } as any);

    expect(stateRegistry.register.calls.allArgs().map(([state]) => state.name)).toEqual([
      'home',
      'home.applications',
      'home.applications.application',
    ]);
  });

  it('does not register the same state name more than once', () => {
    const stateRegistry = { register: jasmine.createSpy('register') };
    const helper = new StateHelper(stateRegistry as any);

    helper.setNestedState({ name: 'home' } as any);
    helper.setNestedState({ name: 'home' } as any);

    expect(stateRegistry.register).toHaveBeenCalledTimes(1);
  });

  it('rewrites relative view names against the parent state name', () => {
    const stateRegistry = { register: jasmine.createSpy('register') };
    const helper = new StateHelper(stateRegistry as any);

    helper.setNestedState({
      name: 'home',
      children: [
        {
          name: 'applications',
          children: [{ name: 'application', views: { '../main@': { component: 'Application' } } }],
        },
      ],
    } as any);

    const applicationState = stateRegistry.register.calls.mostRecent().args[0];
    expect(applicationState.views['home.main@']).toEqual({ component: 'Application' });
    expect(applicationState.views['../main@']).toBeUndefined();
  });

  it('rewrites relative view targets against the parent state name', () => {
    const stateRegistry = { register: jasmine.createSpy('register') };
    const helper = new StateHelper(stateRegistry as any);

    helper.setNestedState({
      name: 'home',
      children: [
        {
          name: 'applications',
          children: [
            {
              name: 'application',
              children: [
                {
                  name: 'insight',
                  children: [
                    {
                      name: 'clusters',
                      children: [{ name: 'instanceDetails', views: { 'detail@../insight': { component: 'Details' } } }],
                    },
                  ],
                },
              ],
            },
          ],
        },
      ],
    } as any);

    const instanceDetailsState = stateRegistry.register.calls.mostRecent().args[0];
    expect(instanceDetailsState.views['detail@home.applications.application.insight']).toEqual({
      component: 'Details',
    });
    expect(instanceDetailsState.views['detail@../insight']).toBeUndefined();
  });
});
