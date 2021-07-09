import React from 'react';
import { ShallowWrapper, shallow } from 'enzyme';
import { mock } from 'angular';

import { REACT_MODULE } from '../../reactShims';

import { IExecutionParametersProps, ExecutionParameters, IDisplayableParameter } from './ExecutionParameters';

describe('<ExecutionParameters/>', () => {
  let component: ShallowWrapper<IExecutionParametersProps>;

  beforeEach(mock.module(REACT_MODULE));
  beforeEach(mock.inject(() => {})); // Angular is lazy.

  it(`show only pin params, but there's no pinnedDisplayableParameters should return null`, function () {
    const parameters: IDisplayableParameter[] = [{ key: '1', value: 'a' }];

    component = shallow(
      <ExecutionParameters
        pinnedDisplayableParameters={[]}
        displayableParameters={parameters}
        shouldShowAllParams={false}
      />,
    );

    expect(component.get(0)).toEqual(null);
  });

  it(`show only pinned parameters in 2 columns format`, function () {
    const parameters: IDisplayableParameter[] = [
      { key: '1', value: 'a' },
      { key: '2', value: 'b' },
    ];

    component = shallow(
      <ExecutionParameters
        pinnedDisplayableParameters={parameters}
        displayableParameters={[]}
        shouldShowAllParams={false}
      />,
    );

    expect(component.find('.execution-parameters-column').length).toEqual(2);
    expect(component.find('.parameter-key').length).toEqual(2);
    expect(component.find('.parameter-value').length).toEqual(2);
  });

  it(`show all params, but there's no displayableParameters should return null`, function () {
    const parameters: IDisplayableParameter[] = [{ key: '1', value: 'a' }];

    component = shallow(
      <ExecutionParameters
        pinnedDisplayableParameters={parameters}
        displayableParameters={[]}
        shouldShowAllParams={true}
      />,
    );

    expect(component.get(0)).toEqual(null);
  });

  it(`show all parameters in 2 columns format`, function () {
    const parameters: IDisplayableParameter[] = [
      { key: '1', value: 'a' },
      { key: '2', value: 'b' },
    ];

    component = shallow(
      <ExecutionParameters
        pinnedDisplayableParameters={[]}
        displayableParameters={parameters}
        shouldShowAllParams={true}
      />,
    );

    expect(component.find('.params-title').text()).toEqual('Parameters');
    expect(component.find('.execution-parameters-column').length).toEqual(2);
    expect(component.find('.parameter-key').length).toEqual(2);
    expect(component.find('.parameter-value').length).toEqual(2);
  });
});
