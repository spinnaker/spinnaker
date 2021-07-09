import React from 'react';
import { shallow } from 'enzyme';
import { mockInstance } from '@spinnaker/mocks';

import { InstanceInformation } from './InstanceInformation';

describe('InstanceInformation', () => {
  const testInstance = {
    ...mockInstance,
    instanceType: 'm5.large',
    capacityType: 'spot',
    region: 'us-east-1',
    serverGroup: 'test_sg',
  };

  it('should render correct state when all attributes exist', () => {
    const wrapper = shallow(
      <InstanceInformation
        account={testInstance.account}
        availabilityZone={testInstance.availabilityZone}
        instanceType={testInstance.instanceType}
        capacityType={testInstance.capacityType}
        launchTime={testInstance.launchTime}
        provider={testInstance.provider}
        region={testInstance.region}
        serverGroup={testInstance.serverGroup}
        showInstanceType={true}
      />,
    );

    const labeledValues = wrapper.find('LabeledValue');
    expect(labeledValues.length).toEqual(5);

    expect(wrapper.childAt(0).prop('label')).toEqual('Launched');
    expect(wrapper.childAt(1).prop('label')).toEqual('In');
    expect(wrapper.childAt(2).prop('label')).toEqual('Type');
    expect(wrapper.childAt(3).prop('label')).toEqual('Capacity Type');
    expect(wrapper.childAt(4).prop('label')).toEqual('Server Group');

    expect(wrapper.childAt(0).prop('value')).toEqual('1970-01-14 22:37:37 PST');
    expect(wrapper.childAt(2).prop('value')).toEqual(testInstance.instanceType);
    expect(wrapper.childAt(3).prop('value')).toEqual(testInstance.capacityType);
  });

  it('should render correct state when attributes are missing', () => {
    const wrapper = shallow(
      <InstanceInformation
        account={testInstance.account}
        availabilityZone={undefined}
        instanceType={undefined}
        capacityType={undefined}
        launchTime={undefined}
        provider={testInstance.provider}
        region={testInstance.region}
        serverGroup={undefined}
        showInstanceType={true}
      />,
    );

    const labeledValues = wrapper.find('LabeledValue');
    expect(labeledValues.length).toEqual(3);

    expect(wrapper.childAt(0).prop('label')).toEqual('Launched');
    expect(wrapper.childAt(1).prop('label')).toEqual('In');
    expect(wrapper.childAt(2).prop('label')).toEqual('Type');

    expect(wrapper.childAt(0).prop('value')).toEqual('Unknown');
    expect(wrapper.childAt(2).prop('value')).toEqual('Unknown');
  });
});
