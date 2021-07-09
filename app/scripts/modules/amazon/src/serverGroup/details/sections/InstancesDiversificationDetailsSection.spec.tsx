import React from 'react';
import { shallow } from 'enzyme';
import { mockLaunchTemplate, mockServerGroup } from '@spinnaker/mocks';
import { IAmazonMixedInstancesPolicy, IAmazonServerGroupView, IScalingPolicy } from '../../../domain';
import { InstancesDiversificationDetailsSection } from '../../../index';
import { Application, ApplicationModelBuilder } from '@spinnaker/core';

describe('Instance diversification', () => {
  let app: Application;
  beforeEach(() => {
    app = ApplicationModelBuilder.createApplicationForTests('testapp');
  });

  const serverGroupWithMip = {
    ...mockServerGroup,
    mixedInstancesPolicy: {
      instancesDiversification: {
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
    const wrapper = shallow(<InstancesDiversificationDetailsSection serverGroup={serverGroupWithLt} app={app} />);

    expect(wrapper.isEmptyRender()).toEqual(true);
  });

  it('should render for server group with mixed instances policy', () => {
    const wrapper = shallow(<InstancesDiversificationDetailsSection serverGroup={serverGroupWithMip} app={app} />);
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
      let labeledValue = actualLabeledValues.at(index++);
      expect(labeledValue.prop('label')).toEqual(key);
      expect(labeledValue.prop('value')).toEqual(value);
    });
  });

  it('should render for spotInstancePools conditionally', () => {
    const newServerGroup = {
      ...serverGroupWithMip,
    };
    newServerGroup.mixedInstancesPolicy.instancesDiversification.spotAllocationStrategy = 'lowest-price';
    newServerGroup.mixedInstancesPolicy.instancesDiversification.spotInstancePools = 5;

    const wrapper = shallow(<InstancesDiversificationDetailsSection serverGroup={newServerGroup} app={app} />);
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
      let labeledValue = actualLabeledValues.at(index++);
      expect(labeledValue.prop('label')).toEqual(key);
      expect(labeledValue.prop('value')).toEqual(value);
    });
  });
});
