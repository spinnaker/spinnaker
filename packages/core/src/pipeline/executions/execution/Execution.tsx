import { UISref } from '@uirouter/react';
import classNames from 'classnames';
import { isEqual } from 'lodash';
import { $location } from 'ngimport';
import React from 'react';
import { Subscription } from 'rxjs';

import { ExecutionBreadcrumbs } from './ExecutionBreadcrumbs';
import { ExecutionMarker } from './ExecutionMarker';
import { ExecutionPermalink } from './ExecutionPermalink';
import { OrchestratedItemRunningTime } from './OrchestratedItemRunningTime';
import { AccountTag } from '../../../account';
import { Application } from '../../../application/application.model';
import { CancelModal } from '../../../cancelModal/CancelModal';
import { PipelineGraph } from '../../config/graph/PipelineGraph';
import { IExecutionViewState, IPipelineGraphNode } from '../../config/graph/pipelineGraph.service';
import { SETTINGS } from '../../../config/settings';
import { ConfirmationModalService } from '../../../confirmationModal';
import { StageExecutionDetails } from '../../details/StageExecutionDetails';
import { IExecution, IExecutionStageSummary, IPipeline, IRestartDetails } from '../../../domain';
import { ISortFilter } from '../../../filterModel';
import { Overridable } from '../../../overrideRegistry';
import { Tooltip } from '../../../presentation/Tooltip';
import { ReactInjector } from '../../../reactShims';
import { ExecutionState } from '../../../state';
import { ExecutionCancellationReason } from '../../status/ExecutionCancellationReason';
import { ExecutionStatus } from '../../status/ExecutionStatus';
import { ParametersAndArtifacts } from '../../status/ParametersAndArtifacts';
import { logger } from '../../../utils';
import { duration, timestamp } from '../../../utils/timeFormatters';

import './execution.less';

export interface IExecutionProps {
  application: Application;
  execution: IExecution;
  descendantExecutionId?: string;
  showConfigureButton?: boolean;
  pipelineConfig: IPipeline;
  showDurations?: boolean;
  standalone?: boolean;
  title?: string | JSX.Element;
  dataSourceKey?: string;
  showAccountLabels?: boolean;
  onRerun?: (execution: IExecution, config: IPipeline) => void;
  cancelHelpText?: string;
  cancelConfirmationText?: string;
  scrollIntoView?: boolean; // should really only be set to ensure scrolling on initial page load deep link
}

export interface IExecutionState {
  showingDetails: boolean;
  showingParams: boolean;
  pipelinesUrl: string;
  viewState: IExecutionViewState;
  sortFilter: ISortFilter;
  restartDetails: IRestartDetails;
  runningTimeInMs: number;
}

const findChildIndex = (child: string, execution: IExecution) => {
  const result = execution.stageSummaries?.findIndex(
    (s) => s.type === 'pipeline' && s.masterStage?.context?.executionId === child,
  );
  return result;
};

@Overridable('PipelineExecution')
export class Execution extends React.PureComponent<IExecutionProps, IExecutionState> {
  public static defaultProps: Partial<IExecutionProps> = {
    dataSourceKey: 'executions',
    cancelHelpText: 'Cancel execution',
  };

  private stateChangeSuccessSubscription: Subscription;
  private runningTime: OrchestratedItemRunningTime;
  private wrapperRef = React.createRef<HTMLDivElement>();

  constructor(props: IExecutionProps) {
    super(props);
    const { execution, standalone } = this.props;
    const { $stateParams } = ReactInjector;

    const initialViewState = {
      activeStageId: Number($stateParams.stage),
      activeSubStageId: Number($stateParams.subStage),
      executionId: $stateParams.executionId,
      canTriggerPipelineManually: false,
      canConfigure: false,
    };

    // Used when rendering ancestors in SingleExecutionView to mark descendents as "selected"
    if ($stateParams.executionId !== props.execution.id && props.descendantExecutionId) {
      initialViewState.activeStageId = findChildIndex(props.descendantExecutionId, props.execution);
    }

    const restartedStage = execution.stages.find((stage) => stage.context.restartDetails !== undefined);

    this.state = {
      showingDetails: this.invalidateShowingDetails(props, true),
      showingParams: standalone,
      pipelinesUrl: [SETTINGS.gateUrl, 'pipelines/'].join('/'),
      viewState: initialViewState,
      sortFilter: ExecutionState.filterModel.asFilterModel.sortFilter,
      restartDetails: restartedStage ? restartedStage.context.restartDetails : null,
      runningTimeInMs: props.execution.runningTimeInMs,
    };
  }

  private updateViewStateDetails(toParams: any, fromParams: any): void {
    const { descendantExecutionId, execution } = this.props;
    const { viewState } = this.state;

    const shouldShowDetails = toParams.executionId === execution.id;
    const shouldScroll = toParams.executionId === execution.id && fromParams.executionId !== execution.id;
    const newViewState = { ...viewState };
    newViewState.activeStageId = Number(toParams.stage);
    newViewState.activeSubStageId = Number(toParams.subStage);

    // Used when rendering ancestors in SingleExecutionView to mark descendents as "selected"
    if (toParams.executionId !== execution.id && descendantExecutionId) {
      newViewState.activeStageId = findChildIndex(descendantExecutionId, execution);
    }

    if (this.state.showingDetails !== shouldShowDetails) {
      this.setState({
        showingDetails: this.invalidateShowingDetails(this.props, shouldScroll),
        viewState: newViewState,
      });
    } else {
      if (this.state.showingDetails && !isEqual(viewState, newViewState)) {
        this.setState({ viewState: newViewState });
      }
    }
  }

  private invalidateShowingDetails(props = this.props, forceScroll = false): boolean {
    const { $state, $stateParams, executionService } = ReactInjector;
    const { execution, application, standalone } = props;
    const showing =
      standalone === true || (execution.id === $stateParams.executionId && $state.includes('**.execution.**'));
    if (showing && !execution.hydrated) {
      executionService.hydrate(application, execution).then(() => {
        this.setState({ showingDetails: true }, () => this.scrollIntoView(forceScroll));
      });
      return false;
    }
    if (forceScroll) {
      this.scrollIntoView(true);
    }
    return showing;
  }

  public isActive(stage: IExecutionStageSummary): boolean {
    if (!stage) {
      return false;
    }

    // When execution.id doesn't match, we're' rendering the ancestors in <SingleExecutionDetails>
    if (this.props.execution.id !== ReactInjector.$stateParams.executionId) {
      return (
        this.props.descendantExecutionId &&
        stage &&
        stage.type === 'pipeline' &&
        stage.masterStage?.context?.executionId === this.props.descendantExecutionId
      );
    }
    return this.state.showingDetails && Number(ReactInjector.$stateParams.stage) === stage.index;
  }

  public toggleDetails = (stageIndex?: number, subIndex?: number): void => {
    const { executionService } = ReactInjector;
    const { execution, application } = this.props;
    executionService.hydrate(application, execution).then(() => {
      executionService.toggleDetails(execution, stageIndex, subIndex);
    });
  };

  public getUrl(): string {
    let url = $location.absUrl();
    if (!this.props.standalone) {
      url = url.replace('/executions', '/executions/details');
    }
    return url;
  }

  public deleteExecution(): void {
    const { executionService } = ReactInjector;
    ConfirmationModalService.confirm({
      header: 'Really delete execution?',
      buttonText: 'Delete',
      body: '<p>This will permanently delete the execution history.</p>',
      submitMethod: () =>
        executionService.deleteExecution(this.props.application, this.props.execution.id).then(() => {
          if (this.props.standalone) {
            ReactInjector.$state.go('^');
          }
        }),
    });
  }

  public cancelExecution(): void {
    const { application, execution, cancelConfirmationText } = this.props;
    const { executionService } = ReactInjector;
    const hasDeployStage =
      execution.stages &&
      execution.stages.some((stage) => stage.type === 'deploy' || stage.type === 'cloneServerGroup');
    CancelModal.confirm({
      header: `Really stop execution of ${execution.name}?`,
      buttonText: `Stop running ${execution.name}`,
      body:
        hasDeployStage && !cancelConfirmationText
          ? '*Note:* Any deployments that have begun will continue and need to be cleaned up manually.'
          : cancelConfirmationText,
      submitMethod: (reason, force) => executionService.cancelExecution(application, execution.id, force, reason),
    });
  }

  public pauseExecution(): void {
    const { executionService } = ReactInjector;
    ConfirmationModalService.confirm({
      header: 'Really pause execution?',
      buttonText: 'Pause',
      submitMethod: () => executionService.pauseExecution(this.props.application, this.props.execution.id),
    });
  }

  public resumeExecution(): void {
    const { executionService } = ReactInjector;
    ConfirmationModalService.confirm({
      header: 'Really resume execution?',
      buttonText: 'Resume',
      submitMethod: () => executionService.resumeExecution(this.props.application, this.props.execution.id),
    });
  }

  public componentDidMount(): void {
    const { execution } = this.props;
    this.runningTime = new OrchestratedItemRunningTime(execution, (time: number) =>
      this.setState({ runningTimeInMs: time }),
    );
    this.stateChangeSuccessSubscription = ReactInjector.stateEvents.stateChangeSuccess.subscribe(
      ({ toParams, fromParams }) => {
        this.updateViewStateDetails(toParams, fromParams);
      },
    );
  }

  public componentWillReceiveProps(nextProps: IExecutionProps): void {
    if (nextProps.execution !== this.props.execution) {
      this.runningTime.checkStatus(nextProps.execution);
      this.setState({
        showingDetails: this.invalidateShowingDetails(nextProps),
      });
    }
  }

  public componentWillUnmount(): void {
    this.runningTime.reset();
    this.stateChangeSuccessSubscription.unsubscribe();
  }

  private handleNodeClick = (node: IPipelineGraphNode, subIndex: number): void => {
    this.toggleDetails(node.index, subIndex);
  };

  private handleSourceNoStagesClick = (): void => {
    logger.log({ category: 'Pipeline', action: 'Execution source clicked (no stages found)' });
  };

  private handlePauseClick = (): void => {
    logger.log({ category: 'Pipeline', action: 'Execution pause clicked' });
    this.pauseExecution();
  };

  private handleResumeClick = (): void => {
    logger.log({ category: 'Pipeline', action: 'Execution resume clicked' });
    this.resumeExecution();
  };

  private handleDeleteClick = (): void => {
    logger.log({ category: 'Pipeline', action: 'Execution delete clicked' });
    this.deleteExecution();
  };

  private handleCancelClick = (): void => {
    logger.log({ category: 'Pipeline', action: 'Execution cancel clicked' });
    this.cancelExecution();
  };

  private handleRerunClick = (): void => {
    logger.log({ category: 'Pipeline', action: 'Execution rerun clicked' });
    const { application, execution } = this.props;
    const pipelineConfig = application.pipelineConfigs.data.find((p: IPipeline) => p.id === execution.pipelineConfigId);
    this.props.onRerun(execution, pipelineConfig);
  };

  private handleSourceClick = (): void => {
    logger.log({ category: 'Pipeline', action: 'Execution source clicked' });
  };

  private handleToggleDetails = (showingDetails: boolean): void => {
    logger.log({ category: 'Pipeline', action: 'Execution details toggled (Details link)' });
    showingDetails ? this.toggleDetails() : this.toggleDetails(0, 0);
  };

  private handleConfigureClicked = (e: React.MouseEvent<HTMLElement>): void => {
    const { application, execution } = this.props;
    logger.log({ category: 'Execution', action: 'Configuration' });
    ReactInjector.$state.go('^.pipelineConfig', {
      application: application.name,
      pipelineId: execution.pipelineConfigId,
    });
    e.stopPropagation();
  };

  private scrollIntoView = (forceScroll = false) => {
    const element = this.wrapperRef.current;
    const { scrollIntoView, execution } = this.props;
    // use a timeout to let Angular render the execution details before scrolling it into view
    (scrollIntoView || forceScroll) &&
      element &&
      execution.hydrated &&
      setTimeout(() => {
        element.scrollIntoView();
        // nudge it back down to accommodate the group header
        const parent = element.closest('.all-execution-groups');
        parent && (parent.scrollTop -= 45);
      });
  };

  public render() {
    const {
      application,
      descendantExecutionId,
      execution,
      showAccountLabels,
      showConfigureButton,
      showDurations,
      standalone,
      title,
      cancelHelpText,
      pipelineConfig,
    } = this.props;
    const { pipelinesUrl, restartDetails, showingDetails, sortFilter, viewState } = this.state;
    const { $state, $stateParams } = ReactInjector;

    const accountLabels = this.props.execution.deploymentTargets.map((account) => (
      <AccountTag key={account} account={account} />
    ));

    const executionMarkerWidth = `${100 / execution.stageSummaries.length}%`;
    const showExecutionName = standalone || (!title && sortFilter.groupBy !== 'name');
    const executionMarkers = execution.stageSummaries.map((stage, index, allStages) => (
      <ExecutionMarker
        key={stage.refId}
        application={application}
        execution={execution}
        stage={stage}
        onClick={this.toggleDetails}
        active={this.isActive(stage)}
        previousStageActive={this.isActive(index > 0 ? allStages[index - 1] : null)}
        width={executionMarkerWidth}
      />
    ));

    const className = classNames({
      execution: true,
      'show-details': showingDetails,
      'details-hidden': !showingDetails,
      'show-durations': showDurations,
    });

    const hasParentExecution = !!execution.trigger?.parentExecution;

    return (
      <div className={className} id={`execution-${execution.id}`} ref={this.wrapperRef}>
        <div className={`execution-overview group-by-${sortFilter.groupBy}`}>
          <div className="flex-container-h">
            {(title || showExecutionName) && (
              <h4 className="execution-name">
                {(showAccountLabels || showExecutionName) && accountLabels}
                {execution.fromTemplate && <i className="from-template fa fa-table" title="Pipeline from template" />}
                {title || execution.name}
              </h4>
            )}
            {showConfigureButton && (
              <div className="flex-pull-right">
                <Tooltip value="Navigate to Pipeline Configuration">
                  <UISref
                    to="^.pipelineConfig"
                    params={{ application: application.name, pipelineId: execution.pipelineConfigId }}
                  >
                    <button
                      className="btn btn-xs btn-default single-execution-details__configure"
                      onClick={this.handleConfigureClicked}
                    >
                      <span className="glyphicon glyphicon-cog" />
                      <span className="visible-md-inline visible-lg-inline"> Configure</span>
                    </button>
                  </UISref>
                </Tooltip>
              </div>
            )}
          </div>
          {hasParentExecution && (
            <div className="execution-breadcrumbs">
              <ExecutionBreadcrumbs execution={execution} />
            </div>
          )}
          <ExecutionStatus execution={execution} showingDetails={showingDetails} standalone={standalone} />
          <div className="execution-bar">
            <div className="stages">
              {executionMarkers}
              {!execution.stageSummaries.length && (
                <div className="text-center">
                  No stages found.{' '}
                  <a onClick={this.handleSourceNoStagesClick} target="_blank" href={pipelinesUrl + execution.id}>
                    View as JSON
                  </a>
                </div>
              )}
            </div>
            <div className="execution-summary">
              Status:{' '}
              <span className={`status execution-status execution-status-${execution.status.toLowerCase()}`}>
                {execution.status}
                {execution.status === 'NOT_STARTED' && execution.limitConcurrent && (
                  <>
                    {' ('}
                    waiting on{' '}
                    <UISref
                      to={`${$state.current.name.endsWith('.execution') ? '^' : ''}.^.executions`}
                      params={{ pipeline: execution.name, status: 'RUNNING' }}
                    >
                      <a>RUNNING</a>
                    </UISref>{' '}
                    executions)
                  </>
                )}
              </span>
              {execution.cancellationReason && (
                <Tooltip value="See Cancellation Reason below for additional details.">
                  <span className="glyphicon glyphicon-info-sign" />
                </Tooltip>
              )}
              {execution.canceledBy && (
                <span>
                  {' '}
                  by {execution.canceledBy} &mdash; {timestamp(execution.endTime)}
                </span>
              )}
              {restartDetails && (
                <span>
                  {' '}
                  Restarted by {restartDetails.restartedBy} &mdash; {timestamp(restartDetails.restartTime)}
                </span>
              )}
              <span className="pull-right">Duration: {duration(execution.runningTimeInMs)}</span>
            </div>
          </div>
          <div className="execution-actions">
            {execution.isRunning && (
              <Tooltip value="Pause execution">
                <button className="link" onClick={this.handlePauseClick}>
                  <i className="fa fa-pause" />
                </button>
              </Tooltip>
            )}
            {execution.isPaused && (
              <Tooltip value="Resume execution">
                <button className="link" onClick={this.handleResumeClick}>
                  <i className="fa fa-play" />
                </button>
              </Tooltip>
            )}
            {(!execution.isActive || application.attributes.enableRerunActiveExecutions) && this.props.onRerun && (
              <Tooltip value="Re-run execution with same parameters">
                <button className="link" onClick={this.handleRerunClick}>
                  <i className="fa fa-redo" />
                </button>
              </Tooltip>
            )}
            {!execution.isActive && (
              <Tooltip value="Delete execution">
                <button className="link" onClick={this.handleDeleteClick}>
                  <span className="glyphicon glyphicon-trash" />
                </button>
              </Tooltip>
            )}
            {execution.isActive && (
              <Tooltip value={cancelHelpText}>
                <button className="link" onClick={this.handleCancelClick}>
                  <i className="far fa-times-circle" />
                </button>
              </Tooltip>
            )}
          </div>

          {execution.cancellationReason && (
            <ExecutionCancellationReason cancellationReason={execution.cancellationReason} />
          )}

          <ParametersAndArtifacts
            execution={execution}
            expandParamsOnInit={standalone && !descendantExecutionId}
            pipelineConfig={pipelineConfig}
          />

          {!standalone && (
            <div className="execution-details-button">
              <a className="clickable" onClick={() => this.handleToggleDetails(showingDetails)}>
                <span
                  className={`small glyphicon ${showingDetails ? 'glyphicon-chevron-down' : 'glyphicon-chevron-right'}`}
                />
                Execution Details
              </a>
            </div>
          )}
        </div>

        {showingDetails && (
          <div className="execution-graph">
            <PipelineGraph execution={execution} onNodeClick={this.handleNodeClick} viewState={viewState} />
          </div>
        )}
        {showingDetails && (!standalone || execution.id === $stateParams.executionId) && (
          <div className="execution-details-container">
            <StageExecutionDetails execution={execution} application={application} standalone={standalone} />
            <div className="permalinks">
              <div className="permalinks-content">
                <a onClick={this.handleSourceClick} target="_blank" href={pipelinesUrl + execution.id}>
                  View as JSON
                </a>
                {' | '}
                <ExecutionPermalink standalone={standalone} />
              </div>
            </div>
          </div>
        )}
      </div>
    );
  }
}
