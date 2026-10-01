import type { Mock } from 'vitest';
import { shallow } from 'enzyme';
import React from 'react';
import { Subject } from 'rxjs';

import { ExecutionGroupComponent } from './ExecutionGroup';
import { CollapsibleSectionStateCache } from '../../../cache';
import { ExecutionState } from '../../../state';

describe('ExecutionGroup', () => {
  const deckRuntimeServices = {
    executionService: { getSectionCacheKey: () => 'section-key' },
  } as any;
  const application = {
    name: 'test-app',
    pipelineConfigs: { data: [] },
    pipelineLocks: { data: [], onRefresh: vi.fn().mockReturnValue(() => undefined) },
    strategyConfigs: { data: [] },
  } as any;
  const group = { executions: [], heading: 'Pipeline', runningExecutions: [] } as any;
  let previousFilterModel: any;

  beforeEach(() => {
    previousFilterModel = ExecutionState.filterModel;
    ExecutionState.filterModel = {
      asFilterModel: { sortFilter: { groupBy: 'name' } },
      expandSubject: new Subject<boolean>(),
    } as any;
    vi.spyOn(CollapsibleSectionStateCache, 'isSet').mockReturnValue(false);
  });

  afterEach(() => {
    ExecutionState.filterModel = previousFilterModel;
  });

  it('configures the pipeline through the injected state service', () => {
    const injectedGo = vi.fn();
    const stateName = 'home.applications.application.pipelines.executions';
    const component = shallow(
      <ExecutionGroupComponent
        deckRuntimeServices={deckRuntimeServices}
        {...({
          router: {},
          stateParams: {},
          stateService: { current: { name: stateName }, go: injectedGo, includes: () => false },
        } as any)}
        application={application}
        group={group}
        parent={null}
      />,
      { disableLifecycleMethods: true },
    );

    (component.instance() as any).configure('pipeline-id');

    expect(injectedGo).toHaveBeenCalledWith('^.pipelineConfig', { pipelineId: 'pipeline-id' });
  });

  it('observes route changes through the injected router', () => {
    const injectedUnsubscribe = vi.fn();
    const injectedOnSuccess = vi.fn().mockReturnValue(injectedUnsubscribe);
    const component = shallow(
      <ExecutionGroupComponent
        deckRuntimeServices={deckRuntimeServices}
        {...({
          router: { transitionService: { onSuccess: injectedOnSuccess } },
          stateParams: {},
          stateService: { includes: () => false },
        } as any)}
        application={application}
        group={group}
        parent={null}
      />,
      { disableLifecycleMethods: true },
    );
    const instance = component.instance() as ExecutionGroupComponent;

    instance.componentDidMount();
    instance.componentWillUnmount();

    expect(injectedOnSuccess).toHaveBeenCalledWith({}, expect.any(Function));
    expect(injectedUnsubscribe).toHaveBeenCalled();
  });

  it('expands and marks the group from transition target params', () => {
    let transitionSuccess: (transition: any) => void;
    const injectedOnSuccess = vi.fn().mockImplementation((_criteria: any, callback: (transition: any) => void) => {
      transitionSuccess = callback;
      return () => undefined;
    });
    (CollapsibleSectionStateCache.isSet as Mock).mockReturnValue(true);
    vi.spyOn(CollapsibleSectionStateCache, 'isExpanded').mockReturnValue(false);
    const component = shallow(
      <ExecutionGroupComponent
        deckRuntimeServices={deckRuntimeServices}
        {...({
          router: { transitionService: { onSuccess: injectedOnSuccess } },
          stateParams: {},
          stateService: { includes: () => true },
        } as any)}
        application={application}
        group={{ ...group, executions: [{ id: 'execution-id', deploymentTargets: [] }] }}
        parent={null}
      />,
      { disableLifecycleMethods: true },
    );
    const instance = component.instance() as ExecutionGroupComponent;
    instance.componentDidMount();

    transitionSuccess({
      from: () => ({}),
      params: () => ({ executionId: 'execution-id' }),
      to: () => ({}),
    });

    expect(component.state('open')).toBe(true);
    expect(component.state('showingDetails')).toBe(true);
    expect(component.find('.execution-group').hasClass('showing-details')).toBe(true);
    instance.componentWillUnmount();
  });
});
