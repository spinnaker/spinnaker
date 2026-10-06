import type { Mock } from 'vitest';
import { act, fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { Subject } from 'rxjs';

import { ExecutionGroupComponent } from './ExecutionGroup';
import type { Application } from '../../../application';
import { DeckRuntimeContext } from '../../../bootstrap/DeckRuntimeContext';
import { CollapsibleSectionStateCache } from '../../../cache';
import type { IExecution, IExecutionGroup, IPipeline } from '../../../domain';
import type { IRouterInjectedProps } from '../../../navigation/routerContext';
import { ExecutionState } from '../../../state';
import { renderWithRouter } from '../../../utils/testUtils/rtl';

class IntersectionObserverStub implements IntersectionObserver {
  public readonly root = null;
  public readonly rootMargin = '';
  public readonly thresholds = [];
  public disconnect = vi.fn();
  public observe = vi.fn();
  public takeRecords = vi.fn().mockReturnValue([]);
  public unobserve = vi.fn();
}

describe('ExecutionGroup', () => {
  const executionService = {
    getSectionCacheKey: () => 'section-key',
    hydrate: vi.fn().mockResolvedValue(undefined),
    toggleDetails: vi.fn(),
  };
  const runtime = ({ services: { executionService } } as unknown) as React.ContextType<typeof DeckRuntimeContext>;
  const pipeline = { id: 'pipeline-id', name: 'Pipeline' } as IPipeline;
  const application = {
    attributes: {},
    executions: { data: [] },
    name: 'test-app',
    pipelineConfigs: { data: [] },
    pipelineLocks: { data: [], onRefresh: vi.fn().mockReturnValue(() => undefined) },
    strategyConfigs: { data: [pipeline] },
  } as Application;
  const execution = {
    deploymentTargets: [],
    hydrated: true,
    id: 'execution-id',
    name: 'Pipeline',
    runningTimeInMs: 0,
    stageSummaries: [],
    stages: [],
    status: 'SUCCEEDED',
    trigger: { artifacts: [], parameters: {}, resolvedExpectedArtifacts: [] },
  } as IExecution;
  const group = {
    config: pipeline,
    executions: [],
    heading: 'Pipeline',
    runningExecutions: [],
  } as IExecutionGroup;
  let previousFilterModel: typeof ExecutionState.filterModel;

  function renderGroup(
    options: {
      group?: IExecutionGroup;
      includes?: boolean;
      onSuccess?: Mock;
      stateParams?: IRouterInjectedProps['stateParams'];
      stateName?: string;
    } = {},
  ) {
    const stateService = ({
      current: { name: options.stateName ?? 'home.applications.application.pipelines.executions' },
      go: vi.fn(),
      includes: () => options.includes ?? false,
    } as unknown) as IRouterInjectedProps['stateService'];
    const router = ({
      transitionService: { onSuccess: options.onSuccess ?? vi.fn().mockReturnValue(() => undefined) },
    } as unknown) as IRouterInjectedProps['router'];
    const rendered = renderWithRouter(
      <DeckRuntimeContext.Provider value={runtime}>
        <ExecutionGroupComponent
          application={application}
          deckRuntimeServices={runtime.services}
          group={options.group ?? group}
          parent={null}
          router={router}
          stateParams={options.stateParams ?? {}}
          stateService={stateService}
        />
      </DeckRuntimeContext.Provider>,
    );
    return { ...rendered, stateService };
  }

  beforeEach(() => {
    window.IntersectionObserver = IntersectionObserverStub;
    previousFilterModel = ExecutionState.filterModel;
    ExecutionState.filterModel = {
      asFilterModel: { sortFilter: { groupBy: 'name' } },
      expandSubject: new Subject<boolean>(),
    } as typeof ExecutionState.filterModel;
    vi.spyOn(CollapsibleSectionStateCache, 'isSet').mockReturnValue(false);
    vi.spyOn(CollapsibleSectionStateCache, 'setExpanded').mockReturnValue(undefined);
  });

  afterEach(() => {
    ExecutionState.filterModel = previousFilterModel;
  });

  it('configures the pipeline through the injected state service', () => {
    const { stateService } = renderGroup();

    fireEvent.click(screen.getByText('Configure'));

    expect(stateService.go).toHaveBeenCalledWith('^.pipelineConfig', { pipelineId: 'pipeline-id' });
  });

  it('observes route changes through the injected router', () => {
    const unsubscribe = vi.fn();
    const onSuccess = vi.fn().mockReturnValue(unsubscribe);
    const { unmount } = renderGroup({ onSuccess });

    expect(onSuccess).toHaveBeenCalledWith({}, expect.any(Function));
    unmount();
    expect(unsubscribe).toHaveBeenCalled();
  });

  it('expands and marks the group from transition target params', () => {
    let transitionSuccess: (transition: {
      from: () => object;
      params: () => { executionId: string };
      to: () => object;
    }) => void;
    const onSuccess = vi.fn().mockImplementation((_criteria: object, callback: typeof transitionSuccess) => {
      transitionSuccess = callback;
      return () => undefined;
    });
    vi.mocked(CollapsibleSectionStateCache.isSet).mockReturnValue(true);
    vi.spyOn(CollapsibleSectionStateCache, 'isExpanded').mockReturnValue(false);
    const { container } = renderGroup({
      group: { ...group, executions: [execution] },
      includes: true,
      onSuccess,
    });
    expect(screen.queryByText('Status:')).not.toBeInTheDocument();

    act(() =>
      transitionSuccess({
        from: () => ({}),
        params: () => ({ executionId: 'execution-id' }),
        to: () => ({}),
      }),
    );

    expect(container.querySelector('.execution-group')).toHaveClass('showing-details');
    expect(screen.getByText('Status:')).toBeVisible();
    expect(CollapsibleSectionStateCache.setExpanded).toHaveBeenCalledWith('section-key', true);
  });
});
