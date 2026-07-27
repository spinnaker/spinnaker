import { shallow } from 'enzyme';
import React from 'react';

import { Markdown } from '@spinnaker/core';

import { CanaryExecutionSummaryComponent } from './CanaryExecutionSummary';

describe('CanaryExecutionSummary', () => {
  const summaryProps = {
    application: { executions: { data: [] } },
    execution: { id: 'execution-id', isRunning: false, limitConcurrent: false, pipelineConfigId: 'pipeline-id' },
    stage: { id: 'stage-id', name: 'Canary', isRunning: true, isCompleted: false },
    stageSummary: {
      endTime: 2,
      masterStage: { context: { canary: { status: { status: 'SUCCEEDED' } } }, exceptions: [] },
      masterStageIndex: 2,
      name: 'Canary',
      runningTimeInMs: 3,
      stages: [],
      startTime: 1,
      type: 'canary',
    },
  } as any;

  it('renders comments through sanitized Markdown', () => {
    const comments = '<img src=x onerror=alert(1)> reviewer note';
    const component = shallow(
      <CanaryExecutionSummaryComponent
        {...({ router: {}, stateParams: {}, stateService: {} } as any)}
        {...summaryProps}
        stageSummary={{ ...summaryProps.stageSummary, comments }}
      />,
    );

    expect(component.find(Markdown).prop('message')).toBe(comments);
    expect(component.findWhere((node) => !!node.prop('dangerouslySetInnerHTML')).exists()).toBe(false);
  });

  it('navigates step details through the injected route', () => {
    const go = jasmine.createSpy('go');
    const component = shallow(
      <CanaryExecutionSummaryComponent
        {...summaryProps}
        {...({
          router: {},
          stateParams: { stage: '3', step: '1', subStage: '4' },
          stateService: { go },
        } as any)}
      />,
    );

    component.find('tbody tr').first().simulate('click');

    expect(go).toHaveBeenCalledWith('.', { stage: 3, step: 2, subStage: 4 });
  });
});
