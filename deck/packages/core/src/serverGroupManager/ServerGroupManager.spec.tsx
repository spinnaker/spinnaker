import { hashLocationPlugin, servicesPlugin, UIRouterContext, UIRouterReact } from '@uirouter/react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';
import { Subject } from 'rxjs';

import { ServerGroupManagerComponent } from './ServerGroupManager';
import { ClusterState } from '../state';

describe('<ServerGroupManager />', () => {
  const serverGroup = {
    account: 'k8s-local',
    buildInfo: { images: [] },
    cloudProvider: 'kubernetes',
    cluster: 'backend',
    instanceCounts: {
      up: 2,
      down: 0,
      starting: 0,
      succeeded: 0,
      failed: 0,
      unknown: 0,
      outOfService: 0,
    },
    instances: [],
    moniker: { sequence: 1 },
    name: 'backend-65b97dd546',
    region: 'dev',
    runningExecutions: [],
    runningTasks: [],
    type: 'kubernetes',
  } as any;
  let previousFilterService: any;
  let previousMultiselectModel: any;
  let router: UIRouterReact;

  beforeEach(() => {
    previousFilterService = ClusterState.filterService;
    previousMultiselectModel = ClusterState.multiselectModel;
    ClusterState.filterService = { shouldShowInstance: () => true } as any;
    ClusterState.multiselectModel = {
      instancesStream: new Subject<void>(),
      serverGroupsStream: new Subject<void>(),
      serverGroupIsSelected: () => false,
    } as any;
    router = new UIRouterReact();
    router.plugin(servicesPlugin);
    router.plugin(hashLocationPlugin);
  });

  afterEach(() => {
    ClusterState.filterService = previousFilterService;
    ClusterState.multiselectModel = previousMultiselectModel;
    router.dispose();
  });

  it('links grouped server group managers to manager details', async () => {
    const originalUrl = window.location.href;
    window.history.replaceState(null, '', '#/applications/kubernetesapp/clusters');

    try {
      render(
        <UIRouterContext.Provider value={router}>
          <ServerGroupManagerComponent
            application={{ name: 'kubernetesapp' } as any}
            grouping={{ hasDiscovery: false, hasLoadBalancers: false } as any}
            manager="deployment backend"
            serverGroups={[serverGroup]}
            sortFilter={{ listInstances: false, multiselect: false, showAllInstances: false } as any}
            router={router}
            stateParams={{}}
            stateService={{ includes: () => false } as any}
          />
        </UIRouterContext.Provider>,
      );

      const link = screen.getByRole('link', { name: /deployment backend/ });
      const expectedHref =
        '#/applications/kubernetesapp/clusters/serverGroupManagerDetails/kubernetes/k8s-local/dev/deployment%20backend';
      expect(link).toHaveAttribute('href', expectedHref);

      await userEvent.click(link);

      expect(window.location.hash).toBe(expectedHref);
    } finally {
      window.history.replaceState(null, '', originalUrl);
    }
  });
});
