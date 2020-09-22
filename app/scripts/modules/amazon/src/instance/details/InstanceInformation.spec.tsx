import React from 'react';
import { shallow } from 'enzyme';
import { mockInstance } from '@spinnaker/mocks';

import { InstanceInformation } from './InstanceInformation';

describe('InstanceInformation', () => {
  const testInstance = {
    ...mockInstance,
    instanceType: 'm5.large',
    region: 'us-east-1',
    serverGroup: 'test_sg',
  };

  it('should render correct state when all attributes exist', () => {
    const wrapper = shallow(
      <InstanceInformation
        account={testInstance.account}
        availabilityZone={testInstance.availabilityZone}
        instanceType={testInstance.instanceType}
        launchTime={testInstance.launchTime}
        provider={testInstance.provider}
        region={testInstance.region}
        serverGroup={testInstance.serverGroup}
      />,
    );

    const labeledValues = wrapper.find('LabeledValue');
    expect(labeledValues.length).toEqual(4);

    expect(wrapper.childAt(0).prop('value')).toEqual('1970-01-14 22:37:37 PST');
    expect(wrapper.childAt(2).prop('value')).toEqual(testInstance.instanceType);
    expect(wrapper.childAt(3).prop('label')).toEqual('Server Group');
  });

  it('should render correct state when attributes are missing', () => {
    const wrapper = shallow(
      <InstanceInformation
        account={testInstance.account}
        availabilityZone={undefined}
        instanceType={undefined}
        launchTime={undefined}
        provider={testInstance.provider}
        region={testInstance.region}
        serverGroup={undefined}
      />,
    );

    const labeledValues = wrapper.find('LabeledValue');
    expect(labeledValues.length).toEqual(3);

    expect(wrapper.childAt(0).prop('value')).toEqual('Unknown');
    expect(wrapper.childAt(2).prop('value')).toEqual('Unknown');
    expect(wrapper.childAt(2).prop('label')).not.toEqual('Server Group');
  });
});
