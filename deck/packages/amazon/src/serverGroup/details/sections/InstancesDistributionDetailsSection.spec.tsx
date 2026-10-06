import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';

import type { Application } from '@spinnaker/core';
import { CollapsibleSectionStateCache } from '@spinnaker/core';
import { mockLaunchTemplate, mockServerGroup } from '@spinnaker/mocks';

import type { IAmazonMixedInstancesPolicy, IAmazonServerGroupView, IScalingPolicy } from '../../../domain';
import { InstancesDistributionDetailsSection } from '../../../index';

describe('InstancesDistribution', () => {
  const app = {} as Application;
  const expectValue = (label: string, value: string) => {
    const term = screen.getByText(label, { selector: 'dt' });
    expect(term.nextElementSibling).toHaveTextContent(value);
  };

  const serverGroupWithMip = {
    ...mockServerGroup,
    mixedInstancesPolicy: {
      instancesDistribution: {
        onDemandAllocationStrategy: 'prioritized',
        onDemandBaseCapacity: 1,
        onDemandPercentageAboveBaseCapacity: 50,
        spotAllocationStrategy: 'capacity-optimized',
        spotMaxPrice: '1.5',
      },
      launchTemplates: [mockLaunchTemplate],
    } as IAmazonMixedInstancesPolicy,
    scalingPolicies: [] as IScalingPolicy[],
  } as IAmazonServerGroupView;

  beforeEach(() => vi.spyOn(CollapsibleSectionStateCache, 'isSet').mockReturnValue(false));

  it('should NOT render for server group without mixed instances policy ', () => {
    const serverGroupWithLt = {
      ...mockServerGroup,
      launchTemplate: mockLaunchTemplate,
    } as IAmazonServerGroupView;
    const { container } = render(<InstancesDistributionDetailsSection serverGroup={serverGroupWithLt} app={app} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('should render for server group with mixed instances policy', () => {
    render(<InstancesDistributionDetailsSection serverGroup={serverGroupWithMip} app={app} />);
    fireEvent.click(screen.getByText('Instances Distribution'));
    expectValue('On-Demand Allocation Strategy', 'prioritized');
    expectValue('On-Demand Base Capacity', '1');
    expectValue('On-Demand Percentage Above Base Capacity', '50');
    expectValue('Spot Allocation Strategy', 'capacity-optimized');
    expectValue('Max Spot Price', '1.5');
  });

  it('should render for spotInstancePools conditionally', () => {
    const newServerGroup = {
      ...serverGroupWithMip,
    };
    newServerGroup.mixedInstancesPolicy.instancesDistribution.spotAllocationStrategy = 'lowest-price';
    newServerGroup.mixedInstancesPolicy.instancesDistribution.spotInstancePools = 5;

    render(<InstancesDistributionDetailsSection serverGroup={newServerGroup} app={app} />);
    fireEvent.click(screen.getByText('Instances Distribution'));
    expectValue('Spot Allocation Strategy', 'lowest-price');
    expectValue('Spot Instance Pools', '5');
  });
});
