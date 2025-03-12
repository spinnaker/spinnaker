import { shallow } from 'enzyme';
import React from 'react';

import type { Application } from '@spinnaker/core';

import { AWSProviderSettings } from '../../aws.settings';
import type { IAmazonFunction } from '../../index';
import { FunctionActions } from '../../index';

describe('FunctionActions', () => {
  it('should render correct state when all attributes exist', () => {
    const app = { name: 'app' } as Application;

    const functionDef = { functionName: 'app-function' } as IAmazonFunction;
    AWSProviderSettings.adHocInfraWritesEnabled = true;

    const wrapper = shallow(
      <FunctionActions
        app={app}
        functionDef={functionDef}
        functionFromParams={{
          account: 'test-account',
          region: 'us-east-1',
          functionName: 'function-name',
        }}
      />,
    );

    const dropDown = wrapper.find('DropdownToggle');

    expect(dropDown.childAt(0).text()).toEqual('Function Actions');
  });

  it('should not render DropdownToggle if aws.adHocInfraWritesEnabled is false', () => {
    const app = { name: 'app' } as Application;

    const functionDef = { functionName: 'app-function' } as IAmazonFunction;
    AWSProviderSettings.adHocInfraWritesEnabled = false;

    const wrapper = shallow(
      <FunctionActions
        app={app}
        functionDef={functionDef}
        functionFromParams={{
          account: 'test-account',
          region: 'us-east-1',
          functionName: 'function-name',
        }}
      />,
    );

    const dropDown = wrapper.find('div');
    expect(dropDown.children().length).toEqual(0);
  });
});
