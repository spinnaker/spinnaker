import { UIRouterReact } from '@uirouter/react';

import { StandaloneSecurityGroupDetails } from './StandaloneSecurityGroupDetails';
import { getStandaloneFirewallState } from './securityGroup.states';
import { createDeckRuntime } from '../bootstrap/DeckRuntime';
import { setDirectRouter } from '../navigation/directRouter';
import { configureRouter } from '../navigation/router';

describe('security group states', () => {
  const routers: UIRouterReact[] = [];

  afterEach(() => {
    routers.splice(0).forEach((router) => router.dispose());
    setDirectRouter(null);
  });

  it('uses the React standalone security group details wrapper for standalone firewall routes', () => {
    const state = getStandaloneFirewallState({} as any);

    expect(state.views['main@']).toEqual(
      jasmine.objectContaining({
        component: StandaloneSecurityGroupDetails,
        $type: 'react',
      }),
    );
    expect(state.views['main@'].templateUrl).toBeUndefined();
    expect(state.views['main@'].controllerProvider).toBeUndefined();
  });

  it('resolves a standalone firewall through the direct security group reader', async () => {
    const securityGroupsIndex = { aws: { prod: {} } };
    const loadSecurityGroups = jasmine.createSpy('loadSecurityGroups').and.resolveTo(securityGroupsIndex);
    const router = new UIRouterReact();
    const runtime = createDeckRuntime(router);
    spyOn(runtime.services.securityGroupReader, 'loadSecurityGroups').and.callFake(loadSecurityGroups);
    router.disposable({ dispose: runtime.dispose });
    configureRouter(router, runtime.services);
    routers.push(router);

    await router.stateService.go(
      'home.firewallDetails',
      {
        accountId: 'prod',
        name: 'web',
        provider: 'aws',
        region: 'eu-west-1',
        vpcId: 'vpc-1',
      },
      { location: false },
    );

    const app = router.globals.successfulTransitions.peekTail().injector().get('app') as any;
    expect(router.stateService.current.name).toBe('home.firewallDetails');
    expect(loadSecurityGroups).toHaveBeenCalledTimes(1);
    expect(app.name).toBe('web');
    expect(app.securityGroupsIndex).toBe(securityGroupsIndex);
  });
});
