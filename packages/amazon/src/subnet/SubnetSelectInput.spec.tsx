import { mock } from 'angular';
import { render, shallow } from 'enzyme';
import React from 'react';

import type { Application } from '@spinnaker/core';
import { ApplicationModelBuilder } from '@spinnaker/core';
import { mockServerGroupDataSourceConfig, mockSubnet } from '@spinnaker/mocks';

import { SubnetSelectInput } from './SubnetSelectInput';

describe('SubnetSelectInput', () => {
  let application: Application;

  beforeEach(
    mock.inject(() => {
      application = ApplicationModelBuilder.createApplicationForTests('testapp', mockServerGroupDataSourceConfig);
    }),
  );

  // Selectable state
  const INPUT_PROPS = {
    value: 'test',
    onChange: () => {},
    application,
    readOnly: false,
    hideClassic: false,
    subnets: [mockSubnet],
    region: 'us-east-1',
    credentials: 'test_credentials',
  };

  it('should render an input', () => {
    const wrapper = shallow(<SubnetSelectInput {...INPUT_PROPS} />);
    const input = wrapper.find('SelectInput');
    const paragraph = wrapper.find('p');
    expect(input.length).toBeTruthy();
    expect(paragraph.length).toBeFalsy();
  });

  it('should generate options', () => {
    const wrapper = render(<SubnetSelectInput {...INPUT_PROPS} />);
    expect(wrapper.find('option').length).toBeGreaterThan(0);
  });
});

describe('SubnetSelectInput read only', () => {
  let application: Application;

  beforeEach(
    mock.inject(() => {
      application = ApplicationModelBuilder.createApplicationForTests('testapp', mockServerGroupDataSourceConfig);
    }),
  );

  // Read only state, with value
  const READ_ONLY_PROPS = {
    value: 'test',
    onChange: () => {},
    application,
    readOnly: true,
    hideClassic: false,
    subnets: [mockSubnet],
    region: 'us-east-1',
    credentials: 'test_credentials',
  };

  // Read only state with null value
  const NULL_VALUE_PROPS = {
    ...READ_ONLY_PROPS,
    value: '',
  };

  it('should render the value', () => {
    const wrapper = shallow(<SubnetSelectInput {...READ_ONLY_PROPS} />);

    const input = wrapper.find('SelectInput');
    const paragraph = wrapper.find('p');
    expect(input.length).toBeFalsy();
    expect(paragraph.length).toBeTruthy();
    expect(paragraph.text()).toEqual(READ_ONLY_PROPS.value);
  });

  it('should render the default value', () => {
    const wrapper = shallow(<SubnetSelectInput {...NULL_VALUE_PROPS} />);
    const input = wrapper.find('SelectInput');
    const paragraph = wrapper.find('p');
    expect(input.length).toBeFalsy();
    expect(paragraph.length).toBeTruthy();
    expect(paragraph.text()).toEqual('None (EC2 Classic)');
  });
});
