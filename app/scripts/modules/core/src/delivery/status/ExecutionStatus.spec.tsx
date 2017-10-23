import * as React from 'react';
import { ShallowWrapper, shallow } from 'enzyme';
import { mock, noop } from 'angular';

import { EXECUTION_FILTER_MODEL } from 'core/delivery/filter/executionFilter.model';
import { REACT_MODULE } from 'core/reactShims';

import { IExecution } from 'core/domain';

import { IExecutionStatusProps, IExecutionStatusState, ExecutionStatus } from './ExecutionStatus';

describe('<ExecutionStatus/>', () => {

  let component: ShallowWrapper<IExecutionStatusProps, IExecutionStatusState>;

  beforeEach(mock.module(EXECUTION_FILTER_MODEL, REACT_MODULE));
  beforeEach(mock.inject(() => {})); // Angular is lazy.

  function getNewExecutionStatus(execution: IExecution): ShallowWrapper<IExecutionStatusProps, IExecutionStatusState> {
    return shallow(
      <ExecutionStatus
        execution={execution}
        toggleDetails={noop}
        showingDetails={false}
        standalone={false}
      />
    );
  }

  it('adds parameters, sorted alphabetically, to vm if present on trigger', function () {
    const execution: IExecution = {
      trigger: {
        parameters: {
          a: 'b',
          b: 'c',
          d: 'a',
        }
      }
    } as any;
    component = getNewExecutionStatus(execution);
    expect(component.state().parameters).toEqual([
      { key: 'a', value: '"b"' },
      { key: 'b', value: '"c"' },
      { key: 'd', value: '"a"' }
    ]);
  });

  it('does not add parameters to vm if none present in trigger', function () {
    const execution: IExecution = { trigger: { } } as any;
    component = getNewExecutionStatus(execution);
    expect(component.state().parameters).toEqual([]);
  });

  it('excludes some parameters if the pipeline is a strategy', function () {
    const execution: IExecution = {
      isStrategy: true,
      trigger: {
        parameters: {
          included: 'a',
          parentPipelineId: 'b',
          strategy: 'c',
          parentStageId: 'd',
          deploymentDetails: 'e',
          cloudProvider: 'f'
        }
      }
    } as any;
    component = getNewExecutionStatus(execution);
    expect(component.state().parameters).toEqual([
      { key: 'included', value: '"a"' }
    ]);
  });
});
