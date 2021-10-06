import { shallow } from 'enzyme';
import React from 'react';

import type { Application } from '@spinnaker/core';
import { ApplicationModelBuilder } from '@spinnaker/core';
import { mockLaunchTemplate, mockServerGroup } from '@spinnaker/mocks';

import type { IAmazonMixedInstancesPolicy, IAmazonServerGroupView, IScalingPolicy } from '../../../domain';
import { InstancesDistributionDetailsSection } from '../../../index';

describe('InstancesDistribution', () => {
  let app: Application;
  beforeEach(() => {
    app = ApplicationModelBuilder.createApplicationForTests('testapp');
  });

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

  it('should NOT render for server group without mixed instances policy ', () => {
    const serverGroupWithLt = {
      ...mockServerGroup,
      launchTemplate: mockLaunchTemplate,
    } as IAmazonServerGroupView;
    const wrapper = shallow(<InstancesDistributionDetailsSection serverGroup={serverGroupWithLt} app={app} />);

    expect(wrapper.isEmptyRender()).toEqual(true);
  });

  it('should render for server group with mixed instances policy', () => {
    const wrapper = shallow(<InstancesDistributionDetailsSection serverGroup={serverGroupWithMip} app={app} />);
    expect(wrapper.isEmptyRender()).toEqual(false);

    const actualLabeledValues = wrapper.find('LabeledValue');
    expect(actualLabeledValues.length).toEqual(5);
    const expectedLabels = new Map<string, any>([
      ['On-Demand Allocation Strategy', 'prioritized'],
      ['On-Demand Base Capacity', 1],
      ['On-Demand Percentage Above Base Capacity', 50],
      ['Spot Allocation Strategy', 'capacity-optimized'],
    ]);
    let index = 0;
    expectedLabels.forEach((value, key) => {
      const labeledValue = actualLabeledValues.at(index++);
      expect(labeledValue.prop('label')).toEqual(key);
      expect(labeledValue.prop('value')).toEqual(value);
    });
  });

  it('should render for spotInstancePools conditionally', () => {
    const newServerGroup = {
      ...serverGroupWithMip,
    };
    newServerGroup.mixedInstancesPolicy.instancesDistribution.spotAllocationStrategy = 'lowest-price';
    newServerGroup.mixedInstancesPolicy.instancesDistribution.spotInstancePools = 5;

    const wrapper = shallow(<InstancesDistributionDetailsSection serverGroup={newServerGroup} app={app} />);
    expect(wrapper.isEmptyRender()).toEqual(false);

    const actualLabeledValues = wrapper.find('LabeledValue');
    expect(actualLabeledValues.length).toEqual(6);
    const expectedLabels = new Map<string, any>([
      ['On-Demand Allocation Strategy', 'prioritized'],
      ['On-Demand Base Capacity', 1],
      ['On-Demand Percentage Above Base Capacity', 50],
      ['Spot Allocation Strategy', 'lowest-price'],
      ['Spot Instance Pools', 5],
    ]);
    let index = 0;
    expectedLabels.forEach((value, key) => {
      const labeledValue = actualLabeledValues.at(index++);
      expect(labeledValue.prop('label')).toEqual(key);
      expect(labeledValue.prop('value')).toEqual(value);
    });
  });
});
