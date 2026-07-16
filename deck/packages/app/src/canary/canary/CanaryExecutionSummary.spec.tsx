import { shallow } from 'enzyme';
import React from 'react';

import { Markdown, ReactInjector } from '@spinnaker/core';

import { CanaryExecutionSummary } from './CanaryExecutionSummary';

describe('CanaryExecutionSummary', () => {
  let originalInjector: any;

  beforeEach(() => {
    originalInjector = (ReactInjector as any).$injector;
    ReactInjector.initialize({
      get: (name: string) => {
        if (name === '$stateParams') {
          return {};
        }
        return originalInjector.get(name);
      },
    } as any);
  });

  afterEach(() => {
    (ReactInjector as any).$injector = originalInjector;
  });

  it('renders comments through sanitized Markdown', () => {
    const comments = '<img src=x onerror=alert(1)> reviewer note';
    const Component = CanaryExecutionSummary as React.ComponentType<any>;
    const component = shallow(
      <Component
        application={{ executions: { data: [] } }}
        execution={{ id: 'execution-id', isRunning: false, limitConcurrent: false, pipelineConfigId: 'pipeline-id' }}
        stage={{ id: 'stage-id', name: 'Canary', isRunning: true, isCompleted: false }}
        stageSummary={{
          comments,
          endTime: 2,
          masterStage: { context: { canary: { status: { status: 'SUCCEEDED' } } }, exceptions: [] },
          masterStageIndex: 0,
          name: 'Canary',
          runningTimeInMs: 3,
          stages: [],
          startTime: 1,
          type: 'canary',
        }}
      />,
    );

    expect(component.find(Markdown).prop('message')).toBe(comments);
    expect(component.findWhere((node) => !!node.prop('dangerouslySetInnerHTML')).exists()).toBe(false);
  });
});
