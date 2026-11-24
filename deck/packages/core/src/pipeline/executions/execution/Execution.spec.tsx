import { hashLocationPlugin, servicesPlugin, UIRouterContext, UIRouterReact, UIViewContext } from '@uirouter/react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import React from 'react';

import { Execution } from './Execution';
import type { Application } from '../../../application';
import { DeckRuntimeContext } from '../../../bootstrap/DeckRuntimeContext';
import type { IExecution, IExecutionStage, IExecutionStageSummary } from '../../../domain';
import { ExecutionBarLabel } from '../../config/stages/common/ExecutionBarLabel';
import { ExecutionState } from '../../../state';

describe('Execution', () => {
  const executionService = { hydrate: vi.fn().mockResolvedValue(undefined), toggleDetails: vi.fn() };
  const runtime = ({
    services: {
      executionDetailsSectionService: {
        synchronizeSection: vi.fn((_sections: string[], callback: () => void) => callback()),
      },
      executionService,
    },
  } as unknown) as React.ContextType<typeof DeckRuntimeContext>;
  const application = {
    attributes: {},
    executions: { data: [] },
    name: 'test-app',
    pipelineConfigs: { data: [] },
  } as Application;
  const stage = (index: number, values: Partial<IExecutionStage> = {}): IExecutionStage =>
    ({
      context: {},
      id: `stage-${index}`,
      index,
      name: `Stage ${index}`,
      status: 'SUCCEEDED',
      tasks: [],
      type: 'wait',
      ...values,
    } as IExecutionStage);
  const summary = (index: number, values: Partial<IExecutionStageSummary> = {}): IExecutionStageSummary =>
    ({
      index,
      labelComponent: () => null,
      masterStage: stage(index),
      markerIcon: () => null,
      name: `Stage ${index}`,
      refId: `${index}`,
      stages: [stage(index)],
      status: 'SUCCEEDED',
      suspendedStageTypes: new Set(),
      type: 'wait',
      ...values,
    } as IExecutionStageSummary);
  const groupedSummary = (index: number, subStageCount: number): IExecutionStageSummary =>
    summary(index, {
      groupStages: Array.from({ length: subStageCount }, (_, subStageIndex) =>
        summary(subStageIndex, {
          name: `Stage ${index}.${subStageIndex}`,
          refId: `${index}.${subStageIndex}`,
        }),
      ),
      labelComponent: ExecutionBarLabel,
      masterStage: stage(index, { type: 'group' }),
      type: 'group',
    });
  const execution = {
    deploymentTargets: [],
    hydrated: true,
    id: 'execution-id',
    name: 'Test pipeline',
    runningTimeInMs: 0,
    stageSummaries: [summary(0), summary(1), groupedSummary(2, 4), summary(3), groupedSummary(4, 6), summary(5)],
    stages: [stage(0), stage(1), stage(2), stage(3), stage(4), stage(5)],
    status: 'SUCCEEDED',
    trigger: { artifacts: [], parameters: {}, resolvedExpectedArtifacts: [] },
  } as IExecution;
  const routers: UIRouterReact[] = [];
  let previousFilterModel: typeof ExecutionState.filterModel;

  function createRouter(): UIRouterReact {
    const router = new UIRouterReact();
    router.plugin(servicesPlugin);
    router.plugin(hashLocationPlugin);
    [
      'home',
      'home.applications',
      'home.applications.application',
      'home.applications.application.pipelines',
      'home.applications.application.pipelines.executions',
    ].forEach((name) => router.stateRegistry.register({ name }));
    router.stateRegistry.register({
      name: 'home.applications.application.pipelines.executions.execution',
      url: '/:executionId?stage&subStage&step',
    });
    router.stateRegistry.register({
      name: 'home.applications.application.pipelines.executions.pipelineConfig',
      url: '/configure/:pipelineId',
    });
    routers.push(router);
    return router;
  }

  async function renderExecution(
    stateParams: Record<string, string>,
    options: {
      execution?: IExecution;
      showConfigureButton?: boolean;
      onParentClick?: () => void;
      onSuccessCleanup?: () => void;
    } = {},
  ) {
    const router = createRouter();
    await router.stateService.go('home.applications.application.pipelines.executions.execution', stateParams, {
      location: false,
    });
    if (options.onSuccessCleanup) {
      const register = router.transitionService.onSuccess.bind(router.transitionService);
      vi.spyOn(router.transitionService, 'onSuccess').mockImplementation((criteria, callback, registrationOptions) => {
        const deregister = register(criteria, callback, registrationOptions);
        return () => {
          options.onSuccessCleanup();
          deregister();
        };
      });
    }
    const rendered = render(
      <DeckRuntimeContext.Provider value={runtime}>
        <UIRouterContext.Provider value={router}>
          <UIViewContext.Provider
            value={{
              fqn: 'execution',
              context: router.stateRegistry.get(
                'home.applications.application.pipelines.executions.execution',
              ) as React.ContextType<typeof UIViewContext>['context'],
            }}
          >
            <div onClick={options.onParentClick}>
              <Execution
                application={application}
                execution={options.execution ?? execution}
                pipelineConfig={null}
                showConfigureButton={options.showConfigureButton}
              />
            </div>
          </UIViewContext.Provider>
        </UIRouterContext.Provider>
      </DeckRuntimeContext.Provider>,
    );
    return { ...rendered, router };
  }

  beforeEach(() => {
    previousFilterModel = ExecutionState.filterModel;
    ExecutionState.filterModel = {
      asFilterModel: { sortFilter: { groupBy: 'name' } },
    } as typeof ExecutionState.filterModel;
    HTMLElement.prototype.scrollIntoView = vi.fn();
  });

  afterEach(() => {
    ExecutionState.filterModel = previousFilterModel;
    routers.splice(0).forEach((router) => router.dispose());
  });

  it('derives its initial view from the injected route', async () => {
    const { container } = await renderExecution({ executionId: 'execution-id', stage: '2', subStage: '3', step: '0' });

    expect(container.querySelector('#execution-execution-id')).toHaveClass('show-details');
    expect(container.querySelectorAll('.stages .execution-marker')[2]).toHaveClass('active');
    expect(container.querySelector('.execution-graph svg.pipeline-graph > g.active')).toHaveTextContent(
      'Stage 2: Stage 2.3',
    );
    expect(screen.getByText('Stage details: Stage 2.3')).toBeVisible();
  });

  it('updates its view from injected route transitions', async () => {
    const unsubscribe = vi.fn();
    const { container, router, unmount } = await renderExecution(
      { executionId: 'other-execution', stage: '0', step: '0' },
      { onSuccessCleanup: unsubscribe },
    );
    expect(container.querySelector('#execution-execution-id')).toHaveClass('details-hidden');

    await act(async () => {
      await router.stateService.go(
        'home.applications.application.pipelines.executions.execution',
        { executionId: 'execution-id', stage: '2', subStage: '3', step: '0' },
        { location: false },
      );
    });

    expect(container.querySelector('#execution-execution-id')).toHaveClass('show-details');
    expect(container.querySelector('.execution-graph svg.pipeline-graph > g.active')).toHaveTextContent(
      'Stage 2: Stage 2.3',
    );

    await act(async () => {
      await router.stateService.go(
        'home.applications.application.pipelines.executions.execution',
        { executionId: 'execution-id', stage: '4', subStage: '5', step: '0' },
        { location: false },
      );
    });

    expect(container.querySelectorAll('.stages .execution-marker')[4]).toHaveClass('active');
    expect(container.querySelector('.execution-graph svg.pipeline-graph > g.active')).toHaveTextContent(
      'Stage 4: Stage 4.5',
    );
    expect(screen.getByText('Stage details: Stage 4.5')).toBeVisible();

    unmount();
    expect(unsubscribe).toHaveBeenCalled();
  });

  it('configures the pipeline through the injected state service', async () => {
    const onParentClick = vi.fn();
    const configuredExecution = { ...execution, pipelineConfigId: 'pipeline-id' };
    const { router } = await renderExecution(
      { executionId: 'execution-id', stage: '0', step: '0' },
      { execution: configuredExecution, onParentClick, showConfigureButton: true },
    );
    const go = vi.spyOn(router.stateService, 'go');

    fireEvent.click(screen.getByRole('button', { name: 'Configure pipeline' }));

    expect(go).toHaveBeenCalledWith('^.pipelineConfig', {
      application: 'test-app',
      pipelineId: 'pipeline-id',
    });
    expect(onParentClick).not.toHaveBeenCalled();
  });
});
