import { shallow } from 'enzyme';
import React from 'react';

import { ExecutionComponent } from './Execution';
import { setDirectRouter } from '../../../navigation/directRouter';
import { ExecutionState } from '../../../state';

describe('Execution', () => {
  const deckRuntimeServices = { executionService: {} } as any;
  const application = { name: 'test-app', pipelineConfigs: { data: [] } } as any;
  const execution = {
    deploymentTargets: [],
    hydrated: true,
    id: 'execution-id',
    runningTimeInMs: 0,
    stageSummaries: [],
    stages: [],
    status: 'SUCCEEDED',
  } as any;
  let previousFilterModel: any;

  beforeEach(() => {
    previousFilterModel = ExecutionState.filterModel;
    ExecutionState.filterModel = { asFilterModel: { sortFilter: { groupBy: 'name' } } } as any;
    setDirectRouter({
      globals: { params: { executionId: 'other-execution', stage: '9', subStage: '8' } },
      stateService: { includes: () => false },
    } as any);
  });

  afterEach(() => {
    ExecutionState.filterModel = previousFilterModel;
    setDirectRouter(null);
  });

  it('derives its initial view from the injected route', () => {
    const component = shallow(
      <ExecutionComponent
        deckRuntimeServices={deckRuntimeServices}
        {...({
          router: {},
          stateParams: { executionId: 'execution-id', stage: '2', subStage: '3' },
          stateService: { includes: () => true },
        } as any)}
        application={application}
        execution={execution}
        pipelineConfig={null}
      />,
      { disableLifecycleMethods: true },
    );

    expect(component.state('showingDetails')).toBe(true);
    expect(component.state('viewState')).toEqual(
      jasmine.objectContaining({ activeStageId: 2, activeSubStageId: 3, executionId: 'execution-id' }),
    );
    expect((component.instance() as ExecutionComponent).isActive({ index: 2 } as any)).toBe(true);
  });

  it('updates its view from injected route transitions', () => {
    let transitionHandler: (transition: any) => void;
    const unsubscribe = jasmine.createSpy('unsubscribe');
    const onSuccess = jasmine.createSpy('onSuccess').and.callFake((_criteria, callback) => {
      transitionHandler = callback;
      return unsubscribe;
    });
    const component = shallow(
      <ExecutionComponent
        deckRuntimeServices={deckRuntimeServices}
        {...({
          router: { transitionService: { onSuccess } },
          stateParams: { executionId: 'other-execution' },
          stateService: { includes: () => true },
        } as any)}
        application={application}
        execution={execution}
        pipelineConfig={null}
      />,
      { disableLifecycleMethods: true },
    );
    const instance = component.instance() as ExecutionComponent;
    instance.componentDidMount();

    transitionHandler({
      from: () => ({ name: 'executions' }),
      params: (target: string) =>
        target === 'to'
          ? { executionId: 'execution-id', stage: '4', subStage: '5' }
          : { executionId: 'other-execution' },
      to: () => ({ name: 'executions.execution' }),
    });

    expect(component.state('showingDetails')).toBe(true);
    expect(component.state('viewState')).toEqual(jasmine.objectContaining({ activeStageId: 4, activeSubStageId: 5 }));
    instance.componentWillUnmount();
    expect(unsubscribe).toHaveBeenCalled();
  });

  it('configures the pipeline through the injected state service', () => {
    const go = jasmine.createSpy('go');
    const stopPropagation = jasmine.createSpy('stopPropagation');
    const component = shallow(
      <ExecutionComponent
        deckRuntimeServices={deckRuntimeServices}
        {...({ router: {}, stateParams: {}, stateService: { go, includes: () => false } } as any)}
        application={application}
        execution={{ ...execution, pipelineConfigId: 'pipeline-id' }}
        pipelineConfig={null}
      />,
      { disableLifecycleMethods: true },
    );

    (component.instance() as any).handleConfigureClicked({ stopPropagation });

    expect(go).toHaveBeenCalledWith('^.pipelineConfig', {
      application: 'test-app',
      pipelineId: 'pipeline-id',
    });
    expect(stopPropagation).toHaveBeenCalled();
  });
});
