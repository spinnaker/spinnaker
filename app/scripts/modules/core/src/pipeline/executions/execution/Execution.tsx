import * as React from 'react';
import * as ReactGA from 'react-ga';
import { clone, isEqual } from 'lodash';
import { $location } from 'ngimport';
import { Subscription } from 'rxjs';
import * as classNames from 'classnames';

import { Application } from 'core/application/application.model';
import { CopyToClipboard } from 'core/utils';
import { StageExecutionDetails } from 'core/pipeline/details/StageExecutionDetails';
import { ExecutionStatus } from 'core/pipeline/status/ExecutionStatus';
import { IExecution, IRestartDetails, IPipeline } from 'core/domain';
import { IExecutionViewState, IPipelineGraphNode } from 'core/pipeline/config/graph/pipelineGraph.service';
import { OrchestratedItemRunningTime } from './OrchestratedItemRunningTime';
import { SETTINGS } from 'core/config/settings';
import { AccountTag } from 'core/account';
import { ReactInjector } from 'core/reactShims';
import { duration, timestamp } from 'core/utils/timeFormatters';
import { ISortFilter } from 'core/filterModel';
import { ExecutionState } from 'core/state';

// react components
import { ExecutionMarker } from './ExecutionMarker';
import { PipelineGraph } from 'core/pipeline/config/graph/PipelineGraph';
import { Tooltip } from 'core/presentation/Tooltip';
import { CancelModal } from 'core/cancelModal/CancelModal';

import './execution.less';

export interface IExecutionProps {
  application: Application;
  execution: IExecution;
  showDurations?: boolean;
  standalone?: boolean;
  title?: string | JSX.Element;
  dataSourceKey?: string;
  showAccountLabels?: boolean;
  onRerun?: (execution: IExecution, config: IPipeline) => void;
  cancelHelpText?: string;
  cancelConfirmationText?: string;
}

export interface IExecutionState {
  showingDetails: boolean;
  pipelinesUrl: string;
  viewState: IExecutionViewState;
  sortFilter: ISortFilter;
  restartDetails: IRestartDetails;
  runningTimeInMs: number;
}

export class Execution extends React.Component<IExecutionProps, IExecutionState> {
  public static defaultProps: Partial<IExecutionProps> = {
    dataSourceKey: 'executions',
    cancelHelpText: 'Cancel execution',
  };

  private stateChangeSuccessSubscription: Subscription;
  private runningTime: OrchestratedItemRunningTime;

  constructor(props: IExecutionProps) {
    super(props);
    const { execution } = this.props;
    const { $stateParams } = ReactInjector;

    const initialViewState = {
      activeStageId: Number($stateParams.stage),
      activeSubStageId: Number($stateParams.subStage),
      executionId: $stateParams.executionId,
      canTriggerPipelineManually: false,
      canConfigure: false,
    };

    const restartedStage = execution.stages.find(stage => stage.context.restartDetails !== undefined);

    this.state = {
      showingDetails: this.invalidateShowingDetails(props),
      pipelinesUrl: [SETTINGS.gateUrl, 'pipelines/'].join('/'),
      viewState: initialViewState,
      sortFilter: ExecutionState.filterModel.asFilterModel.sortFilter,
      restartDetails: restartedStage ? restartedStage.context.restartDetails : null,
      runningTimeInMs: props.execution.runningTimeInMs,
    };
  }

  private updateViewStateDetails(): void {
    const { $stateParams } = ReactInjector;
    const { viewState } = this.state;
    const newViewState = clone(viewState);
    newViewState.activeStageId = Number($stateParams.stage);
    newViewState.activeSubStageId = Number($stateParams.subStage);
    newViewState.executionId = $stateParams.executionId;
    if (!isEqual(viewState, newViewState)) {
      this.setState({
        viewState: newViewState,
        showingDetails: this.invalidateShowingDetails(),
      });
    }
  }

  private invalidateShowingDetails(props = this.props): boolean {
    const { $state, $stateParams, executionService } = ReactInjector;
    const { execution, application, standalone } = props;
    const showing =
      standalone === true || (execution.id === $stateParams.executionId && $state.includes('**.execution.**'));
    if (showing && !execution.hydrated) {
      executionService.hydrate(application, execution).then(() => {
        this.setState({ showingDetails: true });
      });
      return false;
    }
    return showing;
  }

  public isActive(stageIndex: number): boolean {
    return this.state.showingDetails && Number(ReactInjector.$stateParams.stage) === stageIndex;
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
    const { confirmationModalService, executionService } = ReactInjector;
    confirmationModalService.confirm({
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
      execution.stages && execution.stages.some(stage => stage.type === 'deploy' || stage.type === 'cloneServerGroup');
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
    const { confirmationModalService, executionService } = ReactInjector;
    confirmationModalService.confirm({
      header: 'Really pause execution?',
      buttonText: 'Pause',
      body:
        '<p>This will pause the pipeline for up to 72 hours.</p><p>After 72 hours the pipeline will automatically timeout and fail.</p>',
      submitMethod: () => executionService.pauseExecution(this.props.application, this.props.execution.id),
    });
  }

  public resumeExecution(): void {
    const { confirmationModalService, executionService } = ReactInjector;
    confirmationModalService.confirm({
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
    this.stateChangeSuccessSubscription = ReactInjector.stateEvents.stateChangeSuccess.subscribe(() =>
      this.updateViewStateDetails(),
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
    ReactGA.event({ category: 'Pipeline', action: 'Execution source clicked (no stages found)' });
  };

  private handlePauseClick = (): void => {
    ReactGA.event({ category: 'Pipeline', action: 'Execution pause clicked' });
    this.pauseExecution();
  };

  private handleResumeClick = (): void => {
    ReactGA.event({ category: 'Pipeline', action: 'Execution resume clicked' });
    this.resumeExecution();
  };

  private handleDeleteClick = (): void => {
    ReactGA.event({ category: 'Pipeline', action: 'Execution delete clicked' });
    this.deleteExecution();
  };

  private handleCancelClick = (): void => {
    ReactGA.event({ category: 'Pipeline', action: 'Execution cancel clicked' });
    this.cancelExecution();
  };

  private handleRerunClick = (): void => {
    ReactGA.event({ category: 'Pipeline', action: 'Execution rerun clicked' });
    const { application, execution } = this.props;
    const pipelineConfig = application.pipelineConfigs.data.find((p: IPipeline) => p.id === execution.pipelineConfigId);
    this.props.onRerun(execution, pipelineConfig);
  };

  private handleSourceClick = (): void => {
    ReactGA.event({ category: 'Pipeline', action: 'Execution source clicked' });
  };

  private handlePermalinkClick = (): void => {
    ReactGA.event({ category: 'Pipeline', action: 'Permalink clicked' });
  };

  public render() {
    const { application, execution, showAccountLabels, showDurations, standalone, title, cancelHelpText } = this.props;
    const { pipelinesUrl, restartDetails, showingDetails, sortFilter, viewState } = this.state;

    const accountLabels = this.props.execution.deploymentTargets.map(account => (
      <AccountTag key={account} account={account} />
    ));

    const executionMarkerWidth = `${100 / execution.stageSummaries.length}%`;
    const showExecutionName = !title && sortFilter.groupBy !== 'name';
    const executionMarkers = execution.stageSummaries.map(stage => (
      <ExecutionMarker
        key={stage.refId}
        application={application}
        execution={execution}
        stage={stage}
        onClick={this.toggleDetails}
        active={this.isActive(stage.index)}
        previousStageActive={this.isActive(stage.index - 1)}
        width={executionMarkerWidth}
      />
    ));

    const className = classNames({
      execution: true,
      'show-details': showingDetails,
      'details-hidden': !showingDetails,
      'show-durations': showDurations,
    });

    return (
      <div className={className} id={`execution-${execution.id}`}>
        <div className={`execution-overview group-by-${sortFilter.groupBy}`}>
          {title && (
            <h4 className="execution-name">
              {showAccountLabels && accountLabels}
              {execution.fromTemplate && <i className="from-template fa fa-table" title="Pipeline from template" />}
              {title}
            </h4>
          )}
          {showExecutionName && (
            <h4 className="execution-name">
              {accountLabels}
              {execution.fromTemplate && <i className="from-template fa fa-table" title="Pipeline from template" />}
              {execution.name}
            </h4>
          )}
          <ExecutionStatus
            execution={execution}
            toggleDetails={this.toggleDetails}
            showingDetails={showingDetails}
            standalone={standalone}
          />
          <div className="execution-bar">
            <div className="stages">
              {executionMarkers}
              {!execution.stageSummaries.length && (
                <div className="text-center">
                  No stages found.
                  <a onClick={this.handleSourceNoStagesClick} target="_blank" href={pipelinesUrl + execution.id}>
                    Source
                  </a>
                </div>
              )}
            </div>
            <div className="execution-summary">
              Status:{' '}
              <span className={`status execution-status execution-status-${execution.status.toLowerCase()}`}>
                {execution.status}
              </span>
              {execution.cancellationReason && (
                <Tooltip value={execution.cancellationReason}>
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
            {!execution.isActive && (
              <span>
                {this.props.onRerun && (
                  <Tooltip value="Re-run execution with same parameters">
                    <button className="link" onClick={this.handleRerunClick}>
                      <i className="fa fa-redo" />
                    </button>
                  </Tooltip>
                )}
                <Tooltip value="Delete execution">
                  <button className="link" onClick={this.handleDeleteClick}>
                    <span className="glyphicon glyphicon-trash" />
                  </button>
                </Tooltip>
              </span>
            )}
            {execution.isActive && (
              <Tooltip value={cancelHelpText}>
                <button className="link" onClick={this.handleCancelClick}>
                  <i className="far fa-times-circle" />
                </button>
              </Tooltip>
            )}
          </div>
        </div>
        {showingDetails && (
          <div className="execution-graph">
            <PipelineGraph execution={execution} onNodeClick={this.handleNodeClick} viewState={viewState} />
          </div>
        )}
        {showingDetails && (
          <div className="execution-details-container">
            <StageExecutionDetails execution={execution} application={application} standalone={standalone} />
            <div className="permalinks">
              <div className="permalinks-content">
                <a onClick={this.handleSourceClick} target="_blank" href={pipelinesUrl + execution.id}>
                  Source
                </a>
                {' | '}
                <a onClick={this.handlePermalinkClick} href={this.getUrl()}>
                  Permalink
                </a>
                <CopyToClipboard text={this.getUrl()} toolTip="Copy permalink to clipboard" />
              </div>
            </div>
          </div>
        )}
      </div>
    );
  }
}
