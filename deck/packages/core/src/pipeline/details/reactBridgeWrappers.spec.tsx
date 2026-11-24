import type { Mock } from 'vitest';
import { UIRouterReact } from '@uirouter/react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { setupUser } from '../../utils/testUtils/userEvent';
import React from 'react';

import { ConfirmationModalService } from '../../confirmationModal';
import type {
  Application,
  IExecution,
  IExecutionDetailsComponentProps,
  IExecutionDetailsProps,
  IExecutionStage,
  IExecutionStageSummary,
  IStageTypeConfig,
} from '../..';
import { DeckRuntimeContext } from '../../bootstrap/DeckRuntimeContext';
import type { IRouterInjectedProps } from '../../navigation/routerContext';
import { setDirectRouter } from '../../navigation/directRouter';
import { Registry } from '../../registry/Registry';
import { renderWithRouter } from '../../utils/testUtils/rtl';
import { ExecutionDetailsSectionNavComponent } from './ExecutionDetailsSectionNav';
import { StageExecutionDetailsComponent } from './StageExecutionDetails';
import { StageSummary } from './StageSummary';
import { StageSummaryWrapper, StageSummaryWrapperComponent } from './StageSummaryWrapper';
import { StepDetails } from './StepDetails';
import { StepExecutionDetailsWrapperComponent } from './StepExecutionDetailsWrapper';

describe('pipeline details bridge wrappers', () => {
  const stateService = ({ go: vi.fn() } as unknown) as IRouterInjectedProps['stateService'];
  const routerProps = {
    router: {} as IRouterInjectedProps['router'],
    stateParams: {},
    stateService,
  };
  const executionService = {
    patchExecution: vi.fn(),
    updateExecution: vi.fn(),
    waitUntilExecutionMatches: vi.fn(),
  };
  const runtime = ({
    services: {
      executionDetailsSectionService: {
        synchronizeSection: vi.fn((_sections: string[], callback: () => void) => callback()),
      },
      executionService,
    },
  } as unknown) as React.ContextType<typeof DeckRuntimeContext>;
  const application = { attributes: {} } as Application;

  const stage = (values: Partial<IExecutionStage> = {}) =>
    ({ context: {}, id: 'stage-id', name: 'Deploy', tasks: [], type: 'deploy', ...values } as IExecutionStage);
  const summary = (values: Partial<IExecutionStageSummary> = {}) =>
    ({ masterStage: stage(), name: 'Deploy', stages: [], type: 'deploy', ...values } as IExecutionStageSummary);
  const execution = (values: Partial<IExecution> = {}) =>
    ({ id: 'execution-id', stageSummaries: [], stages: [], ...values } as IExecution);

  function renderWithRuntime(ui: React.ReactElement) {
    return renderWithRouter(<DeckRuntimeContext.Provider value={runtime}>{ui}</DeckRuntimeContext.Provider>);
  }

  afterEach(() => {
    setDirectRouter(null);
    vi.restoreAllMocks();
  });

  it('renders the direct StageSummaryWrapper without template compatibility props', () => {
    const props = {
      application,
      config: {} as IStageTypeConfig,
      execution: execution(),
      stage: stage(),
      stageSummary: summary(),
    };

    const element = StageSummary(props) as React.ReactElement;
    const wrapper = React.Children.only(element.props.children) as React.ReactElement;

    expect(wrapper.type).toBe(StageSummaryWrapper);
    expect(wrapper.props).toEqual({
      application: props.application,
      execution: props.execution,
      stage: props.stage,
      stageSummary: props.stageSummary,
    });
    expect(wrapper.props).not.toHaveProperty('configSections');
  });

  it('renders StageSummaryWrapper directly with step rows, markdown comments, and current step state', () => {
    const CustomStepLabel = ({ step }: { step: IExecutionStage }) => <span>Custom {step.context.serverGroupName}</span>;
    vi.spyOn(Registry.pipeline, 'getStageConfig').mockReturnValue({
      executionStepLabelComponent: CustomStepLabel,
    } as IStageTypeConfig);

    const { container } = renderWithRuntime(
      <StageSummaryWrapperComponent
        {...routerProps}
        deckRuntimeServices={runtime.services}
        application={application}
        execution={execution()}
        stage={stage()}
        stateParams={{ step: '1' }}
        stageSummary={summary({
          comments: '**approved**',
          runningTimeInMs: 60000,
          stages: [
            stage({ context: { serverGroupName: 'blue' }, name: 'first task', status: 'SUCCEEDED', type: 'task' }),
            stage({ context: { serverGroupName: 'green' }, name: 'second task', status: 'RUNNING', type: 'task' }),
          ],
        })}
      />,
    );

    expect(screen.getByText('Custom blue')).toBeVisible();
    expect(screen.getByText('Custom green')).toBeVisible();
    expect(screen.getByText('approved', { selector: 'strong' })).toBeVisible();
    expect(within(screen.getByRole('row', { name: /Custom green/ })).getByText('Running')).toBeVisible();
    expect(screen.getByRole('row', { name: /Custom green/ })).toHaveClass('info');
    expect(container.querySelectorAll('tbody tr')).toHaveLength(2);
  });

  it('sanitizes stage summary markdown comments', () => {
    const { container } = renderWithRuntime(
      <StageSummaryWrapperComponent
        {...routerProps}
        deckRuntimeServices={runtime.services}
        application={application}
        execution={execution()}
        stage={stage()}
        stageSummary={summary({ comments: '<img src=x onerror="alert(1)"> **safe** <script>alert(2)</script>' })}
      />,
    );

    expect(screen.getByText('safe', { selector: 'strong' })).toBeVisible();
    expect(container.querySelector('script')).not.toBeInTheDocument();
    expect(container.querySelector('[onerror]')).not.toBeInTheDocument();
  });

  it('tracks the active execution details section from injected route params', () => {
    const props = {
      ...routerProps,
      sections: ['firstSection', 'secondSection'],
      stateParams: { details: 'firstSection' },
    };
    const { rerender } = render(<ExecutionDetailsSectionNavComponent {...props} />);

    expect(screen.getByText('First Section')).toHaveClass('active');
    expect(screen.getByText('Second Section')).not.toHaveClass('active');

    rerender(<ExecutionDetailsSectionNavComponent {...props} stateParams={{ details: 'secondSection' }} />);

    expect(screen.getByText('First Section')).not.toHaveClass('active');
    expect(screen.getByText('Second Section')).toHaveClass('active');
  });

  it('navigates execution details sections through the injected state service', async () => {
    const user = setupUser();
    render(
      <ExecutionDetailsSectionNavComponent
        {...routerProps}
        sections={['firstSection', 'secondSection']}
        stateParams={{}}
      />,
    );

    await user.click(screen.getByText('Second Section'));

    expect(stateService.go).toHaveBeenCalledWith('.', { details: 'secondSection' });
  });

  it('resolves deep-linked stages through injected route params and state service', async () => {
    const firstSummary = summary({ name: 'Wait', type: 'wait' });
    const secondSummary = summary({
      masterStage: stage({ id: 'master-stage' }),
      name: 'Deploy selected',
      stages: [stage({ id: 'task-stage' })],
    });

    renderWithRuntime(
      <StageExecutionDetailsComponent
        {...routerProps}
        stateParams={{ stageId: 'task-stage' }}
        application={application}
        execution={execution({ stageSummaries: [firstSummary, secondSummary] })}
      />,
    );

    expect(stateService.go).toHaveBeenCalledWith(
      '.',
      { stage: 1, subStage: undefined, step: 0, stageId: null },
      { location: 'replace' },
    );
    expect(await screen.findByText('Stage details: Deploy selected')).toBeVisible();
  });

  it('selects the next routed stage immediately when route props change', async () => {
    const firstSummary = summary({ name: 'First deploy', stages: [stage({ id: 'first-task' })] });
    const secondSummary = summary({ name: 'Second deploy', stages: [stage({ id: 'second-task' })] });
    const props = {
      ...routerProps,
      application,
      execution: execution({ stageSummaries: [summary({ name: 'Initial' }), firstSummary, secondSummary] }),
    };
    const { rerender } = renderWithRuntime(
      <StageExecutionDetailsComponent {...props} stateParams={{ stageId: 'first-task' }} />,
    );
    expect(await screen.findByText('Stage details: First deploy')).toBeVisible();

    rerender(
      <DeckRuntimeContext.Provider value={runtime}>
        <StageExecutionDetailsComponent {...props} stateParams={{ stageId: 'second-task' }} />
      </DeckRuntimeContext.Provider>,
    );

    expect(await screen.findByText('Stage details: Second deploy')).toBeVisible();
    expect(screen.queryByText('Stage details: First deploy')).not.toBeInTheDocument();
  });

  it('waits for routed props before selecting a stage from a different execution', async () => {
    const firstSummary = summary({ name: 'First deploy', stages: [stage({ id: 'first-task' })] });
    const secondSummary = summary({ name: 'Second deploy', stages: [stage({ id: 'second-task' })] });
    const firstExecution = execution({ id: 'first-execution', stageSummaries: [firstSummary] });
    const secondExecution = execution({ id: 'second-execution', stageSummaries: [summary(), secondSummary] });
    const commonProps = { ...routerProps, application };
    const { rerender } = renderWithRuntime(
      <StageExecutionDetailsComponent
        {...commonProps}
        execution={firstExecution}
        stateParams={{ executionId: 'first-execution', stage: '0', step: '0' }}
      />,
    );
    expect(await screen.findByText('Stage details: First deploy')).toBeVisible();
    (stateService.go as Mock).mockClear();

    rerender(
      <DeckRuntimeContext.Provider value={runtime}>
        <StageExecutionDetailsComponent
          {...commonProps}
          execution={firstExecution}
          stateParams={{ executionId: 'second-execution', stage: '1', step: '0' }}
        />
      </DeckRuntimeContext.Provider>,
    );

    expect(stateService.go).not.toHaveBeenCalled();
    expect(screen.queryByText('Stage details: Second deploy')).not.toBeInTheDocument();

    rerender(
      <DeckRuntimeContext.Provider value={runtime}>
        <StageExecutionDetailsComponent
          {...commonProps}
          execution={secondExecution}
          stateParams={{ executionId: 'second-execution', stage: '1', step: '0' }}
        />
      </DeckRuntimeContext.Provider>,
    );

    expect(await screen.findByText('Stage details: Second deploy')).toBeVisible();
  });

  it('toggles stage summary details through the injected router and preserves stage indices', () => {
    renderWithRuntime(
      <StageSummaryWrapperComponent
        {...routerProps}
        deckRuntimeServices={runtime.services}
        application={application}
        execution={execution()}
        stage={stage()}
        stageSummary={summary({
          stages: [
            stage({ name: 'first task', status: 'SUCCEEDED' }),
            stage({ name: 'second task', status: 'RUNNING' }),
          ],
        })}
        stateParams={{ stage: '2', subStage: '3', step: '0' }}
      />,
    );

    fireEvent.click(screen.getByRole('row', { name: /Second task/ }));

    expect(stateService.go).toHaveBeenCalledWith('.', { stage: 2, step: 1, subStage: 3 });
  });

  it('confirms manual skip against the top-level stage', async () => {
    const updatedExecution = execution({ stages: [stage({ id: 'parent', status: 'SKIPPED' })] });
    executionService.patchExecution.mockResolvedValue(null);
    executionService.waitUntilExecutionMatches.mockResolvedValue(updatedExecution);
    executionService.updateExecution.mockResolvedValue(null);
    vi.spyOn(ConfirmationModalService, 'confirm').mockImplementation((config) =>
      config.submitMethod('operator reason'),
    );

    renderWithRuntime(
      <StageSummaryWrapperComponent
        {...routerProps}
        deckRuntimeServices={runtime.services}
        application={application}
        execution={execution({
          stages: [stage({ id: 'parent', context: { canManuallySkip: true }, name: 'Parent' })],
        })}
        stage={stage({ id: 'child', isRunning: true, parentStageId: 'parent' })}
        stageSummary={summary({ name: 'Child' })}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Skip Parent' }));

    await waitFor(() =>
      expect(executionService.patchExecution).toHaveBeenCalledWith('execution-id', 'parent', {
        manualSkip: true,
        reason: 'operator reason',
      }),
    );
    expect(executionService.updateExecution).toHaveBeenCalledWith(application, updatedExecution);
  });

  it('renders the direct StepExecutionDetailsWrapper with provider and no legacy section props', () => {
    const BridgeDetails = (props: IExecutionDetailsComponentProps) => {
      const hasLegacyConfigSections = Object.prototype.hasOwnProperty.call(props, 'configSections');
      return (
        <div>{`Bridge provider: ${props.provider}; legacy configSections: ${
          hasLegacyConfigSections ? 'present' : 'absent'
        }`}</div>
      );
    };

    renderWithRuntime(
      <StepDetails
        application={application}
        config={{ cloudProvider: 'aws', executionDetailsComponent: BridgeDetails } as IStageTypeConfig}
        execution={execution()}
        stage={stage({ tasks: [stage({ name: 'deploy task', status: 'SUCCEEDED' })] })}
      />,
    );

    expect(screen.getByText('Bridge provider: aws; legacy configSections: absent')).toBeVisible();
    expect(screen.queryByText('Deploy task')).not.toBeInTheDocument();
  });

  it('renders direct execution detail sections instead of the default wrapper', () => {
    const DirectExecutionDetails = Object.assign(
      ({ provider }: IExecutionDetailsProps) => <div>Direct details for {provider}</div>,
      { title: 'Direct' },
    );

    renderWithRuntime(
      <StepDetails
        application={application}
        config={{ cloudProvider: 'aws', executionDetailsSections: [DirectExecutionDetails] } as IStageTypeConfig}
        execution={execution()}
        stage={stage({ tasks: [stage({ name: 'default task', status: 'SUCCEEDED' })] })}
      />,
    );

    expect(screen.getByText('Direct details for aws')).toBeVisible();
    expect(screen.queryByText('Default Task')).not.toBeInTheDocument();
  });

  it('preserves the no-details state when no stage config is registered', () => {
    renderWithRuntime(
      <StepDetails
        application={application}
        config={null}
        execution={execution()}
        stage={stage({ name: 'No config' })}
      />,
    );

    expect(screen.getByRole('heading', { name: 'No config' })).toBeVisible();
    expect(screen.queryByText('Task')).not.toBeInTheDocument();
  });

  it('renders StepExecutionDetailsWrapper default execution details without section nav', () => {
    renderWithRuntime(
      <StepExecutionDetailsWrapperComponent
        {...routerProps}
        application={application}
        execution={execution()}
        stage={stage({ failureMessage: 'it failed', tasks: [stage({ name: 'deploy', status: 'SUCCEEDED' })] })}
      />,
    );

    expect(screen.getByText('Deploy')).toBeVisible();
    expect(screen.getByText('it failed')).toBeVisible();
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
  });

  it('renders custom StepExecutionDetailsWrapper execution details component', () => {
    const CustomExecutionDetails = ({ stage: currentStage }: { stage: IExecutionStage }) => (
      <div>Custom details for {currentStage.context.serverGroupName}</div>
    );

    renderWithRuntime(
      <StepExecutionDetailsWrapperComponent
        {...routerProps}
        application={application}
        config={{ executionDetailsComponent: CustomExecutionDetails } as IStageTypeConfig}
        execution={execution()}
        provider="aws"
        stage={stage({ context: { serverGroupName: 'my-server-group' }, tasks: [stage({ name: 'default task' })] })}
      />,
    );

    expect(screen.getByText('Custom details for my-server-group')).toBeVisible();
    expect(screen.queryByText('Default Task')).not.toBeInTheDocument();
  });

  it('passes the injected details route param to custom step execution details', () => {
    const params = { details: 'facade-details' };
    const directRouter = new UIRouterReact();
    directRouter.globals.params = params;
    setDirectRouter(directRouter);
    const CustomExecutionDetails = ({ currentSection }: { currentSection: string }) => <div>{currentSection}</div>;

    renderWithRuntime(
      <StepExecutionDetailsWrapperComponent
        {...routerProps}
        application={application}
        config={{ executionDetailsComponent: CustomExecutionDetails } as IStageTypeConfig}
        execution={execution()}
        stage={stage()}
        stateParams={{ details: 'injected-details' }}
      />,
    );

    expect(screen.getByText('injected-details')).toBeVisible();
    expect(screen.queryByText('facade-details')).not.toBeInTheDocument();
    directRouter.dispose();
  });
});
