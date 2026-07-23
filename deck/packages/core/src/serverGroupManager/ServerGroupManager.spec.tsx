import { shallow } from 'enzyme';
import React from 'react';

import { ServerGroupManager } from './ServerGroupManager';
import { ServerGroupManagerHeading } from './ServerGroupManagerHeading';
import { AngularServices } from '../angular/services';

describe('<ServerGroupManager />', () => {
  const serverGroup = {
    account: 'k8s-local',
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
    name: 'backend-65b97dd546',
    region: 'dev',
    type: 'kubernetes',
  } as any;

  it('links grouped server group managers to manager details', () => {
    spyOnProperty(AngularServices, '$state', 'get').and.returnValue({ includes: () => false } as any);
    window.location.hash = '#/applications/kubernetesapp/clusters';

    const component = shallow(
      <ServerGroupManager
        application={{ name: 'kubernetesapp' } as any}
        grouping={{} as any}
        manager="deployment backend"
        serverGroups={[serverGroup]}
        sortFilter={{} as any}
      />,
    );

    expect(component.find(ServerGroupManagerHeading).prop('detailsHref')).toBe(
      '#/applications/kubernetesapp/clusters/serverGroupManagerDetails/kubernetes/k8s-local/dev/deployment%20backend',
    );
  });
});
