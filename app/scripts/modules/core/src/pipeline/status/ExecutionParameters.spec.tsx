import * as React from 'react';
import { ShallowWrapper, shallow } from 'enzyme';
import { mock } from 'angular';

import { REACT_MODULE } from 'core/reactShims';

import { IExecutionParametersProps, ExecutionParameters } from './ExecutionParameters';
import { IDisplayableParameter } from 'core/pipeline';
import { IPipeline } from 'core/domain';

describe('<ExecutionParameters/>', () => {
  let component: ShallowWrapper<IExecutionParametersProps>;

  beforeEach(mock.module(REACT_MODULE));
  beforeEach(mock.inject(() => {})); // Angular is lazy.

  it(`show only pin params, but there's no pinnedDisplayableParameters should return null`, function() {
    const parameters: IDisplayableParameter[] = [{ key: '1', value: 'a' }];

    component = shallow(
      <ExecutionParameters
        displayableParameters={parameters}
        pinnedDisplayableParameters={[]}
        shouldShowAllParams={false}
        pipelineConfig={null}
      />,
    );

    expect(component.get(0)).toEqual(null);
  });

  it(`show only pinned parameters in 2 columns format`, function() {
    const parameters: IDisplayableParameter[] = [{ key: '1', value: 'a' }, { key: '2', value: 'b' }];

    component = shallow(
      <ExecutionParameters
        pinnedDisplayableParameters={parameters}
        displayableParameters={[]}
        shouldShowAllParams={false}
        pipelineConfig={null}
      />,
    );

    expect(component.find('.execution-parameters-column').length).toEqual(2);
    expect(component.find('.parameter-key').length).toEqual(2);
    expect(component.find('.parameter-value').length).toEqual(2);
  });

  it(`shows pinned parameters title when all parameters are pinned`, function() {
    const parameters: IDisplayableParameter[] = [{ key: '1', value: 'a' }, { key: '2', value: 'b' }];
    const pipelineConfig: IPipeline = {
      application: 'my-app',
      id: '123-abc',
      index: 0,
      name: 'my-pipeline',
      stages: [],
      keepWaitingPipelines: false,
      limitConcurrent: false,
      strategy: false,
      triggers: [],
      parameterConfig: [],
      pinAllParameters: true,
    };

    component = shallow(
      <ExecutionParameters
        pinnedDisplayableParameters={[]}
        displayableParameters={parameters}
        shouldShowAllParams={false}
        pipelineConfig={pipelineConfig}
      />,
    );

    expect(component.find('.execution-parameters-column').length).toEqual(2);
    expect(component.find('.parameter-key').length).toEqual(2);
    expect(component.find('.parameter-value').length).toEqual(2);
  });

  it(`show all params, but there's no displayableParameters should return null`, function() {
    const parameters: IDisplayableParameter[] = [{ key: '1', value: 'a' }];

    component = shallow(
      <ExecutionParameters
        pinnedDisplayableParameters={parameters}
        displayableParameters={[]}
        shouldShowAllParams={true}
        pipelineConfig={null}
      />,
    );

    expect(component.get(0)).toEqual(null);
  });

  it(`show all parameters in 2 columns format`, function() {
    const parameters: IDisplayableParameter[] = [{ key: '1', value: 'a' }, { key: '2', value: 'b' }];

    component = shallow(
      <ExecutionParameters
        pinnedDisplayableParameters={[]}
        displayableParameters={parameters}
        shouldShowAllParams={true}
        pipelineConfig={null}
      />,
    );

    expect(component.find('.params-title').text()).toEqual('Parameters');
    expect(component.find('.execution-parameters-column').length).toEqual(2);
    expect(component.find('.parameter-key').length).toEqual(2);
    expect(component.find('.parameter-value').length).toEqual(2);
  });
});
