import * as React from 'react';
import * as ReactGA from 'react-ga';
import { clone } from 'lodash';
import { $location } from 'ngimport';
import { Subscription } from 'rxjs';
import { BindAll } from 'lodash-decorators';
import * as classNames from 'classnames';

import { Application } from 'core/application/application.model';
import { ExecutionDetails } from 'core/delivery/details/ExecutionDetails';
import { ExecutionStatus } from 'core/delivery/status/ExecutionStatus';
import { IExecution , IRestartDetails } from 'core/domain';
import { IExecutionViewState, IPipelineGraphNode } from 'core/pipeline/config/graph/pipelineGraph.service';
import { IScheduler } from 'core/scheduler/scheduler.factory';
import { OrchestratedItemRunningTime } from './OrchestratedItemRunningTime';
import { SETTINGS } from 'core/config/settings';
import { NgReact, ReactInjector } from 'core/reactShims';
import { duration, timestamp } from 'core/utils/timeFormatters';

// react components
import { ExecutionMarker } from './ExecutionMarker';
import { PipelineGraph } from 'core/pipeline/config/graph/PipelineGraph';
import { Tooltip } from 'core/presentation/Tooltip';

import './execution.less';

export interface IExecutionProps {
  application: Application;
  execution: IExecution;
  showStageDuration?: boolean;
  standalone?: boolean;
  title?: string;
  dataSourceKey?: string;
  showAccountLabels?: boolean;
}

export interface IExecutionState {
  showingDetails: boolean;
  pipelinesUrl: string;
  viewState: IExecutionViewState;
  sortFilter: any;
  restartDetails: IRestartDetails;
  runningTimeInMs: number;
}

@BindAll()
export class Execution extends React.Component<IExecutionProps, IExecutionState> {
  public static defaultProps: Partial<IExecutionProps> = {
    dataSourceKey: 'executions'
  };

  private activeRefresher: IScheduler;
  private stateChangeSuccessSubscription: Subscription;
  private runningTime: OrchestratedItemRunningTime;

  // Since executionService.getExecution is a promise, we cannot short circuit the .then()
  // callback, set a mounted flag to handle inside the then callback.
  // TODO: Convert executionService.updateExecution() to an Observable and subscribe/unsubscribe
  private mounted: boolean;

  constructor(props: IExecutionProps) {
    super(props);
    const { execution } = this.props;
    const { $stateParams, executionFilterModel } = ReactInjector;

    const initialViewState = {
      activeStageId: Number($stateParams.stage),
      activeSubStageId: Number($stateParams.subStage),
      executionId: execution.id,
      canTriggerPipelineManually: false,
      canConfigure: false,
    };

    const restartedStage = execution.stages.find(stage => stage.context.restartDetails !== undefined);

    this.state = {
      showingDetails: this.invalidateShowingDetails(),
      pipelinesUrl: [SETTINGS.gateUrl, 'pipelines/'].join('/'),
      viewState: initialViewState,
      sortFilter: executionFilterModel.asFilterModel.sortFilter,
      restartDetails: restartedStage ? restartedStage.context.restartDetails : null,
      runningTimeInMs: props.execution.runningTimeInMs
    };
  }

  private updateViewStateDetails(): void {
    const { $stateParams } = ReactInjector;
    const newViewState = clone(this.state.viewState);
    newViewState.activeStageId = Number($stateParams.stage);
    newViewState.activeSubStageId = Number($stateParams.subStage);
    newViewState.executionId = $stateParams.executionId;
    this.setState({
      viewState: newViewState,
      showingDetails: this.invalidateShowingDetails()
    });
  }

  private invalidateShowingDetails(): boolean {
    const { $state, $stateParams } = ReactInjector;
    return (this.props.standalone === true || (this.props.execution.id === $stateParams.executionId &&
      $state.includes('**.execution.**')));
  }

  public isActive(stageIndex: number): boolean {
    return this.state.showingDetails && Number(ReactInjector.$stateParams.stage) === stageIndex;
  }

  public toggleDetails(stageIndex?: number, subIndex?: number): void {
    const { executionService } = ReactInjector;
    executionService.toggleDetails(this.props.execution, stageIndex, subIndex);
  }

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
      submitMethod: () => executionService.deleteExecution(this.props.application, this.props.execution.id).then( () => {
        if (this.props.standalone) {
          ReactInjector.$state.go('^');
        }
      })
    });
  }

  public cancelExecution(): void {
    const { cancelModalService, executionService } = ReactInjector;
    const hasDeployStage = this.props.execution.stages && this.props.execution.stages.some(stage => stage.type === 'deploy' || stage.type === 'cloneServerGroup');
    cancelModalService.confirm({
      header: `Really stop execution of ${this.props.execution.name}?`,
      buttonText: `Stop running ${this.props.execution.name}`,
      body: hasDeployStage ? '<b>Note:</b> Any deployments that have begun will continue and need to be cleaned up manually.' : null,
      submitMethod: (reason, force) => executionService.cancelExecution(this.props.application, this.props.execution.id, force, reason)
    });
  }

  public pauseExecution(): void {
    const { confirmationModalService, executionService } = ReactInjector;
    confirmationModalService.confirm({
        header: 'Really pause execution?',
        buttonText: 'Pause',
        body: '<p>This will pause the pipeline for up to 72 hours.</p><p>After 72 hours the pipeline will automatically timeout and fail.</p>',
        submitMethod: () => executionService.pauseExecution(this.props.application, this.props.execution.id)
    });
  }

  public resumeExecution(): void {
    const { confirmationModalService, executionService } = ReactInjector;
    confirmationModalService.confirm({
        header: 'Really resume execution?',
        buttonText: 'Resume',
        submitMethod: () => executionService.resumeExecution(this.props.application, this.props.execution.id)
    });
  }

  public componentDidMount(): void {
    this.mounted = true;
    this.runningTime = new OrchestratedItemRunningTime(this.props.execution, (time: number) => this.setState({ runningTimeInMs: time }));
    this.stateChangeSuccessSubscription = ReactInjector.stateEvents.stateChangeSuccess.subscribe(() => this.updateViewStateDetails());

    const { execution, application } = this.props;
    const { executionService, schedulerFactory } = ReactInjector;
    if (execution.isRunning && !this.props.standalone) {
      // TODO: This component should not be handling the refreshing itself; it should just be listening.
      this.activeRefresher = schedulerFactory.createScheduler(1000 * Math.ceil(execution.stages.length / 10));
      let refreshing = false;
      this.activeRefresher.subscribe(() => {
        if (refreshing || !execution.isRunning) {
          return;
        }
        refreshing = true;
        executionService.getExecution(execution.id).then((updated: IExecution) => {
          if (this.mounted) {
            const dataSource = application.getDataSource(this.props.dataSourceKey);
            executionService.updateExecution(application, updated, dataSource);
            executionService.removeCompletedExecutionsFromRunningData(application);
          }
          refreshing = false;
        });
      });
    }
  }

  public componentWillReceiveProps(nextProps: IExecutionProps): void {
    if (nextProps.execution !== this.props.execution) {
      this.runningTime.checkStatus(nextProps.execution);
      this.setState({
        showingDetails: this.invalidateShowingDetails()
      });
    }
  }

  public componentWillUnmount(): void {
    this.mounted = false;
    this.runningTime.reset();
    if (this.activeRefresher) { this.activeRefresher.unsubscribe(); }
    this.stateChangeSuccessSubscription.unsubscribe();
  }

  private handleNodeClick(node: IPipelineGraphNode, subIndex: number): void {
    this.toggleDetails(node.index, subIndex);
  }

  private handleSourceNoStagesClick(): void {
    ReactGA.event({category: 'Pipeline', action: 'Execution source clicked (no stages found)'});
  }

  private handlePauseClick(event: React.MouseEvent<HTMLElement>): void {
    ReactGA.event({category: 'Pipeline', action: 'Execution pause clicked'});
    this.pauseExecution();
    event.stopPropagation();
  }

  private handleResumeClick(event: React.MouseEvent<HTMLElement>): void {
    ReactGA.event({category: 'Pipeline', action: 'Execution resume clicked'});
    this.resumeExecution();
    event.stopPropagation();
  }

  private handleDeleteClick(event: React.MouseEvent<HTMLElement>): void {
    ReactGA.event({category: 'Pipeline', action: 'Execution delete clicked'});
    this.deleteExecution();
    event.stopPropagation();
  }

  private handleCancelClick(event: React.MouseEvent<HTMLElement>): void {
    ReactGA.event({category: 'Pipeline', action: 'Execution cancel clicked'});
    this.cancelExecution();
    event.stopPropagation();
  }

  private handleSourceClick(): void {
    ReactGA.event({category: 'Pipeline', action: 'Execution source clicked'});
  }

  private handlePermalinkClick(): void {
    ReactGA.event({category: 'Pipeline', action: 'Permalink clicked'});
  }

  public render() {
    const { AccountTag, CopyToClipboard } = NgReact;
    const accountLabels = this.props.execution.deploymentTargets.map((account) => (
      <AccountTag key={account} account={account}/>
    ));

    const executionMarkerWidth = `${100 / this.props.execution.stageSummaries.length}%`;
    const showExecutionName = !this.props.title && this.state.sortFilter.groupBy !== 'name';
    const executionMarkers = this.props.execution.stageSummaries.map((stage) => (
      <ExecutionMarker
        key={stage.refId}
        application={this.props.application}
        execution={this.props.execution}
        stage={stage}
        onClick={this.toggleDetails}
        active={this.isActive(stage.index)}
        previousStageActive={this.isActive(stage.index - 1)}
        width={executionMarkerWidth}
      />
    ));

    const className = classNames({
      'execution': true,
      'show-details': this.state.showingDetails,
      'details-hidden': !this.state.showingDetails,
      'show-durations': this.props.showStageDuration,
    });

    return (
      <div className={className} id={`execution-${this.props.execution.id}`}>
        <div className={`execution-overview group-by-${this.state.sortFilter.groupBy}`}>
          { this.props.title && (
            <h4 className="execution-name">
              {this.props.showAccountLabels && accountLabels}
              {this.props.title}
            </h4>
          )}
          { showExecutionName && (
            <h4 className="execution-name">
              {accountLabels}
              {this.props.execution.name}
            </h4>
          )}
          <ExecutionStatus
            execution={this.props.execution}
            toggleDetails={this.toggleDetails}
            showingDetails={this.state.showingDetails}
            standalone={this.props.standalone}
          />
          <div className="execution-bar">
            <div className="stages">
              {executionMarkers}
              { !this.props.execution.stageSummaries.length && (
                <div className="text-center">
                  No stages found.
                  <a
                    onClick={this.handleSourceNoStagesClick}
                    target="_blank"
                    href={this.state.pipelinesUrl + this.props.execution.id}
                  >Source
                  </a>
                </div>
              )}
            </div>
            <div className="execution-summary">
              Status: <span className={`status execution-status execution-status-${this.props.execution.status.toLowerCase()}`}>{this.props.execution.status}</span>
              {this.props.execution.cancellationReason && (<Tooltip value={this.props.execution.cancellationReason}><span className="glyphicon glyphicon-info-sign"/></Tooltip>)}
              {this.props.execution.canceledBy && (<span> by {this.props.execution.canceledBy} &mdash; {timestamp(this.props.execution.endTime)}</span>)}
              {this.state.restartDetails && (<span> Restarted by {this.state.restartDetails.restartedBy} &mdash; {timestamp(this.state.restartDetails.restartTime)}</span>)}
              <span className="pull-right">Duration: {duration(this.props.execution.runningTimeInMs)}</span>
            </div>
          </div>
          <div className="execution-actions">
            { this.props.execution.isRunning && (
              <Tooltip value="Pause execution">
                <a className="clickable" onClick={this.handlePauseClick}>
                  <span className="glyphicon glyphicon-pause"/>
                </a>
              </Tooltip>
            )}
            { this.props.execution.isPaused && (
              <Tooltip value="Resume execution">
                <a className="clickable" onClick={this.handleResumeClick}>
                  <span className="glyphicon glyphicon-play"/>
                </a>
              </Tooltip>
            )}
            { !this.props.execution.isActive && (
              <Tooltip value="Delete execution">
                <a className="clickable" onClick={this.handleDeleteClick}>
                  <span className="glyphicon glyphicon-trash"/>
                </a>
              </Tooltip>
            )}
            { this.props.execution.isActive && (
              <Tooltip value="Cancel execution">
                <a className="clickable" onClick={this.handleCancelClick}>
                  <span className="glyphicon glyphicon-remove-circle"/>
                </a>
              </Tooltip>
            )}
          </div>
        </div>
        { this.state.showingDetails && (
          <div className="execution-graph">
            <PipelineGraph
              execution={this.props.execution}
              onNodeClick={this.handleNodeClick}
              viewState={this.state.viewState}
            />
          </div>
        )}
        { this.state.showingDetails && (
          <div className="execution-details-container">
            <ExecutionDetails execution={this.props.execution} application={this.props.application} standalone={this.props.standalone}/>
            <div className="permalinks">
              <div className="permalinks-content">
                <a
                  onClick={this.handleSourceClick}
                  target="_blank"
                  href={this.state.pipelinesUrl + this.props.execution.id}
                >Source
                </a>
                {' | '}
                <a onClick={this.handlePermalinkClick} href={this.getUrl()}>Permalink</a>
                <CopyToClipboard text={this.getUrl()} toolTip="Copy permalink to clipboard"/>
              </div>
            </div>
          </div>
        )}
      </div>
    );
  }
}
