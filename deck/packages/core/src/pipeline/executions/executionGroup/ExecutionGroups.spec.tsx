import type { Mock } from 'vitest';
import { act, render } from '@testing-library/react';
import React from 'react';
import { Subject } from 'rxjs';

import { ExecutionGroupsComponent } from './ExecutionGroups';
import type { Application } from '../../../application';
import { DeckRuntimeContext } from '../../../bootstrap/DeckRuntimeContext';
import { CollapsibleSectionStateCache } from '../../../cache';
import type { IExecution, IExecutionGroup } from '../../../domain';
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

describe('ExecutionGroups', () => {
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
  const group = { executions: [execution], heading: 'Pipeline', runningExecutions: [] } as IExecutionGroup;
  const application = {
    attributes: {},
    banners: { data: [] },
    executions: { data: [], onRefresh: vi.fn().mockReturnValue(() => undefined) },
    name: 'test-app',
    pipelineConfigs: { data: [] },
    pipelineLocks: { data: [], onRefresh: vi.fn().mockReturnValue(() => undefined) },
    strategyConfigs: { data: [] },
  } as Application;
  const runtime = ({
    services: {
      executionService: {
        getSectionCacheKey: () => 'section-key',
        hydrate: vi.fn().mockResolvedValue(undefined),
        toggleDetails: vi.fn(),
      },
    },
  } as unknown) as React.ContextType<typeof DeckRuntimeContext>;
  let previousFilterModel: typeof ExecutionState.filterModel;

  function renderGroups(
    options: { includes?: boolean | (() => boolean); onSuccess?: Mock; stateParams?: { executionId?: string } } = {},
  ) {
    const router = ({
      transitionService: { onSuccess: options.onSuccess ?? vi.fn().mockReturnValue(() => undefined) },
    } as unknown) as IRouterInjectedProps['router'];
    const stateService = ({
      includes: () => (typeof options.includes === 'function' ? options.includes() : options.includes ?? false),
    } as unknown) as IRouterInjectedProps['stateService'];
    return renderWithRouter(
      <DeckRuntimeContext.Provider value={runtime}>
        <ExecutionGroupsComponent
          application={application}
          router={router}
          stateParams={options.stateParams ?? {}}
          stateService={stateService}
        />
      </DeckRuntimeContext.Provider>,
    );
  }

  beforeEach(() => {
    window.IntersectionObserver = IntersectionObserverStub;
    previousFilterModel = ExecutionState.filterModel;
    ExecutionState.filterModel = {
      asFilterModel: { groups: [group], sortFilter: { groupBy: 'name' } },
      expandSubject: new Subject<boolean>(),
    } as typeof ExecutionState.filterModel;
    vi.spyOn(CollapsibleSectionStateCache, 'isSet').mockReturnValue(false);
  });

  afterEach(() => {
    ExecutionState.filterModel = previousFilterModel;
  });

  it('shows details from injected route state when the execution is present', () => {
    const { container } = renderGroups({ includes: true, stateParams: { executionId: 'execution-id' } });

    expect(container.querySelector('.executions')).toHaveClass('showing-details');
  });

  it('observes route changes through the injected router', () => {
    const unsubscribe = vi.fn();
    const onSuccess = vi.fn().mockReturnValue(unsubscribe);
    const { unmount } = renderGroups({ onSuccess });

    expect(onSuccess).toHaveBeenCalledWith({}, expect.any(Function));
    unmount();
    expect(unsubscribe).toHaveBeenCalled();
  });

  it('marks details as shown from transition target params', () => {
    let transitionSuccess: (transition: {
      from: () => object;
      params: () => { executionId: string };
      to: () => object;
    }) => void;
    const onSuccess = vi.fn().mockImplementation((_criteria: object, callback: typeof transitionSuccess) => {
      transitionSuccess = callback;
      return () => undefined;
    });
    let routeIncluded = false;
    const { container } = renderGroups({ includes: () => routeIncluded, onSuccess });
    expect(container.querySelector('.executions')).not.toHaveClass('showing-details');

    routeIncluded = true;
    act(() =>
      transitionSuccess({
        from: () => ({}),
        params: () => ({ executionId: 'execution-id' }),
        to: () => ({}),
      }),
    );

    expect(container.querySelector('.executions')).toHaveClass('showing-details');
  });
});
