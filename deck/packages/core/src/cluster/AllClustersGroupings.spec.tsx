import { act, render } from '@testing-library/react';
import React from 'react';
import { hashLocationPlugin, servicesPlugin, UIRouterContext, UIRouterReact } from '@uirouter/react';
import { CellMeasurerCache, List } from 'react-virtualized';
import { Subject } from 'rxjs';

import { AllClustersGroupingsComponent, findServerGroupRowIndex } from './AllClustersGroupings';
import { ClusterState, initialize } from '../state';

describe('AllClustersGroupings', () => {
  beforeEach(() => initialize());

  it('finds the server group row identified by route params', () => {
    const groups = [
      { subgroups: [{ serverGroups: [{ account: 'account', name: 'server-group', region: 'region' }] }] },
    ] as any;

    expect(
      findServerGroupRowIndex(groups, { accountId: 'account', region: 'region', serverGroup: 'server-group' }),
    ).toBe(0);
    expect(findServerGroupRowIndex(groups, { accountId: 'other', region: 'region', serverGroup: 'server-group' })).toBe(
      -1,
    );
  });

  it('clears cached row heights after a relevant transition from the injected router', async () => {
    const router = new UIRouterReact();
    router.plugin(servicesPlugin);
    router.plugin(hashLocationPlugin);
    router.stateRegistry.register({ name: 'previous', url: '/previous' });
    router.stateRegistry.register({ name: 'home' });
    router.stateRegistry.register({ name: 'home.applications' });
    router.stateRegistry.register({ name: 'home.applications.application' });
    router.stateRegistry.register({ name: 'home.applications.application.insight' });
    router.stateRegistry.register({ name: 'home.applications.application.insight.clusters', url: '/clusters' });
    const clearAll = vi.spyOn(CellMeasurerCache.prototype, 'clearAll');
    const { unmount } = render(
      <UIRouterContext.Provider value={router}>
        <AllClustersGroupingsComponent
          app={{ getDataSource: () => ({ loadFailure: false, data: [], fetchOnDemand: false }) } as any}
          initialized={true}
          router={router}
          stateParams={{}}
          stateService={router.stateService}
        />
      </UIRouterContext.Provider>,
    );

    await act(async () => {
      await router.stateService.go('previous', {}, { location: false });
      await router.stateService.go('home.applications.application.insight.clusters', {}, { location: false });
    });

    expect(clearAll).toHaveBeenCalled();
    unmount();
    router.dispose();
  });

  it('updates row heights through groupsUpdatedStream and unsubscribes on unmount', () => {
    const groupsUpdatedStream = new Subject<any[]>();
    ClusterState.filterModel = { asFilterModel: { groups: [], sortFilter: { listInstances: false } } } as any;
    ClusterState.filterService = { groupsUpdatedStream } as any;
    const router = new UIRouterReact();
    router.plugin(servicesPlugin);
    router.plugin(hashLocationPlugin);
    const recomputeRowHeights = vi.spyOn(List.prototype, 'recomputeRowHeights');
    const { unmount } = render(
      <UIRouterContext.Provider value={router}>
        <AllClustersGroupingsComponent
          app={{ getDataSource: () => ({ loadFailure: false, data: [], fetchOnDemand: false }) } as any}
          initialized={false}
          router={router}
          stateParams={{}}
          stateService={router.stateService}
        />
      </UIRouterContext.Provider>,
    );

    act(() => groupsUpdatedStream.next([{ subgroups: [{ key: 'updated-grouping', subgroups: [] }] }]));
    expect(recomputeRowHeights).toHaveBeenCalledWith(0);
    expect(groupsUpdatedStream.observers).toHaveLength(1);

    unmount();
    expect(groupsUpdatedStream.observers).toHaveLength(0);
    router.dispose();
  });
});
