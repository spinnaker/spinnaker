import { UIRouterReact } from '@uirouter/react';

import { createDeckRuntime } from '../bootstrap/DeckRuntime';
import { ApplicationReader } from '../application/service/ApplicationReader';
import { ClusterMaster } from '../cluster/ClusterMaster';
import { ClusterFilters } from '../cluster/filter/ClusterFilters';
import { setDirectRouter } from '../navigation/directRouter';
import { configureRouter } from '../navigation/router';
import { MultipleServerGroupsDetails } from './details/MultipleServerGroupsDetails';
import { ServerGroupDetailsWrapper } from './details/ServerGroupDetailsWrapper';
import { getClustersState, getMultipleServerGroupsState, getServerGroupDetailsState } from './serverGroup.states';

describe('server group states', () => {
  const routers: UIRouterReact[] = [];

  afterEach(() => {
    routers.splice(0).forEach((router) => router.dispose());
    setDirectRouter(null);
  });

  it('uses React for the clusters insight route', () => {
    const state = getClustersState({ paramsToQuery: () => '', buildDynamicParams: () => ({}) } as any);

    expect(state.views.nav).toEqual(jasmine.objectContaining({ component: ClusterFilters, $type: 'react' }));
    expect(state.views.master).toEqual(jasmine.objectContaining({ component: ClusterMaster, $type: 'react' }));
  });

  it('uses React for single server group details', () => {
    const state = getServerGroupDetailsState();
    const view = state.views['detail@../insight'];

    expect(view).toEqual(jasmine.objectContaining({ component: ServerGroupDetailsWrapper, $type: 'react' }));
    expect(view.templateUrl).toBeUndefined();
    expect(view.controller).toBeUndefined();
    expect(view.controllerAs).toBeUndefined();
  });

  it('uses React for the multiple server groups detail route', () => {
    const state = getMultipleServerGroupsState();
    const view = state.views['detail@../insight'];

    expect(view).toEqual(jasmine.objectContaining({ component: MultipleServerGroupsDetails, $type: 'react' }));
    expect(view.templateUrl).toBeUndefined();
    expect(view.controller).toBeUndefined();
    expect(view.controllerAs).toBeUndefined();
  });

  it('resolves a relative insight detail view during a direct transition', async () => {
    spyOn(ApplicationReader, 'getApplication').and.resolveTo({ name: 'payments', dataSources: [] } as any);
    const router = new UIRouterReact();
    const runtime = createDeckRuntime(router);
    router.disposable(runtime);
    configureRouter(router, runtime.services);
    routers.push(router);

    await router.stateService.go(
      'home.applications.application.insight.clusters.serverGroup',
      {
        accountId: 'prod',
        application: 'payments',
        provider: 'aws',
        region: 'eu-west-1',
        serverGroup: 'payments-v001',
      },
      { location: false },
    );

    const state = router.stateRegistry.get('home.applications.application.insight.clusters.serverGroup');
    const transition = router.globals.successfulTransitions.peekTail();
    expect(router.stateService.current.name).toBe(state.name);
    expect(state.views['detail@home.applications.application.insight']).toBeDefined();
    expect(transition.injector().get('serverGroup')).toEqual({
      accountId: 'prod',
      name: 'payments-v001',
      provider: 'aws',
      region: 'eu-west-1',
    });
  });
});
