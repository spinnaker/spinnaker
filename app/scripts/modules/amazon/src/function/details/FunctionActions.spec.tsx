import React from 'react';
import { shallow } from 'enzyme';

import { Application } from '@spinnaker/core';
import { IAmazonFunction, FunctionActions } from '../../index';

describe('FunctionActions', () => {
  it('should render correct state when all attributes exist', () => {
    let app = { name: 'app' } as Application;

    let functionDef = { functionName: 'app-function' } as IAmazonFunction;

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

    let dropDown = wrapper.find('DropdownToggle');

    expect(dropDown.childAt(0).text()).toEqual('Function Actions');
  });
});
