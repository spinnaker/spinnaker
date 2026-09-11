import { UISref } from '@uirouter/react';
import { shallow } from 'enzyme';
import React from 'react';

import { LoadBalancer } from './LoadBalancer';
import { LoadBalancerClusterContainer } from './LoadBalancerClusterContainer';
import { CloudProviderRegistry } from '../cloudProvider';

describe('LoadBalancer', () => {
  it('carries loadBalancerType in load balancer detail navigation params', () => {
    spyOn(CloudProviderRegistry, 'getValue').and.returnValue({ ClusterContainer: LoadBalancerClusterContainer });

    const loadBalancer = {
      account: 'test-account',
      cloudProvider: 'gce',
      instanceCounts: {},
      loadBalancerType: 'EXTERNAL_MANAGED',
      name: 'regional-map (test-account/us-central1/EXTERNAL_MANAGED)',
      provider: 'gce',
      region: 'us-central1',
      vpcId: null,
    } as any;

    const wrapper = shallow(
      <LoadBalancer
        application={{ name: 'fnord', loadBalancers: { refresh: () => undefined } } as any}
        grouping={{} as any}
        loadBalancer={loadBalancer}
        serverGroups={[]}
      />,
    );

    expect(wrapper.find(UISref).prop('params')).toEqual({
      accountId: 'test-account',
      application: 'fnord',
      loadBalancerType: 'EXTERNAL_MANAGED',
      name: 'regional-map (test-account/us-central1/EXTERNAL_MANAGED)',
      provider: 'gce',
      region: 'us-central1',
      vpcId: null,
    });
  });
});
