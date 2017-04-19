import { clone } from 'lodash';
import { $location, $rootScope } from 'ngimport';
import * as React from 'react';
import * as ReactGA from 'react-ga';

import { Application } from 'core/application/application.model';
import { IExecutionViewState } from 'core/pipeline/config/graph/pipelineGraph.service';
import { IExecution } from 'core/domain/IExecution';
import { IRestartDetails } from 'core/domain/IExecutionStage';
import { OrchestratedItemRunningTime } from './OrchestratedItemRunningTime';
import { confirmationModalService } from 'core/confirmationModal/confirmationModal.service';
import { cancelModalService } from 'core/cancelModal/cancelModal.service';
import { executionService } from 'core/delivery/service/execution.service';
import { executionFilterModel } from 'core/delivery/filter/executionFilter.model';
import { SETTINGS } from 'core/config/settings';
import { schedulerFactory } from 'core/scheduler/scheduler.factory';
import { stateService, stateParamsService } from 'core/state.service';
import { duration, timestamp } from 'core/utils/timeFormatters';

// react components
import { AccountLabelColor } from 'core/account/AccountLabelColor';
import { CopyToClipboard } from 'core/utils/clipboard/CopyToClipboard';
import { ExecutionDetails } from 'core/delivery/details/ExecutionDetails';
import { ExecutionMarker } from './ExecutionMarker';
import { ExecutionStatus } from 'core/delivery/status/ExecutionStatus';
import { PipelineGraph } from 'core/pipeline/config/graph/PipelineGraph';
import { Tooltip } from 'core/presentation/Tooltip';

import './execution.less';

interface IExecutionProps {
  application: Application;
  execution: IExecution;
  standalone: boolean;
}

interface IExecutionState {
  showingDetails: boolean;
  pipelinesUrl: string;
  viewState: IExecutionViewState;
  sortFilter: any;
  restartDetails: IRestartDetails;
  runningTimeInMs: number;
}

export class Execution extends React.Component<IExecutionProps, IExecutionState> {
  private activeRefresher: any;
  private stateChangeSuccessDeregister: () => void;
  private applicationRefreshUnsubscribe: () => void;
  private runningTime: OrchestratedItemRunningTime;

  // Since executionService.getExecution is a promise, we cannot short circuit the .then()
  // callback, set a mounted flag to handle inside the then callback.
  // TODO: Convert executionService.updateExecution() to an Observable and subscribe/unsubscribe
  private mounted: boolean;

  constructor(props: IExecutionProps) {
    super(props);

    const initialViewState = {
      activeStageId: Number(stateParamsService.stage),
      executionId: this.props.execution.id,
      canTriggerPipelineManually: false,
      canConfigure: false,
      showStageDuration: executionFilterModel.sortFilter.showStageDuration,
    };

    const restartedStage = this.props.execution.stages.find(stage => stage.context.restartDetails !== undefined);

    this.state = {
      showingDetails: this.invalidateShowingDetails(),
      pipelinesUrl: [SETTINGS.gateUrl, 'pipelines/'].join('/'),
      viewState: initialViewState,
      sortFilter: executionFilterModel.sortFilter,
      restartDetails: restartedStage ? restartedStage.context.restartDetails : null,
      runningTimeInMs: props.execution.runningTimeInMs
    };

    this.stateChangeSuccessDeregister = $rootScope.$on('$stateChangeSuccess', () => this.updateViewStateDetails());

    if (this.props.execution.isRunning && !this.props.standalone) {
      this.activeRefresher = schedulerFactory.createScheduler(1000 * Math.ceil(this.props.execution.stages.length / 10));
      let refreshing = false;
      this.activeRefresher.subscribe(() => {
        if (refreshing) {
          return;
        }
        refreshing = true;
        executionService.getExecution(this.props.execution.id).then((execution: IExecution) => {
          if (this.mounted) {
            executionService.updateExecution(this.props.application, execution);
            refreshing = false;
          }
        });
      });
    }
  }

  private updateViewStateDetails(): void {
    const newViewState = clone(this.state.viewState);
    newViewState.activeStageId = Number(stateParamsService.stage);
    newViewState.executionId = stateParamsService.executionId;
    this.setState({
      viewState: newViewState,
      showingDetails: this.invalidateShowingDetails()
    });
  };

  private invalidateShowingDetails(): boolean {
    return (this.props.standalone === true || (this.props.execution.id === stateParamsService.executionId &&
      stateService.includes('**.execution.**')));
  }

  public isActive(stageIndex: number): boolean {
    return this.state.showingDetails && Number(stateParamsService.stage) === stageIndex;
  }

  public toggleDetails(node: {executionId: string, index: number}): void {
    if (node.executionId === stateService.params.executionId && stateService.current.name.includes('.executions.execution') && node.index === undefined) {
      stateService.go('^');
      return;
    }
    const index = node.index || 0;
    const params = {
      executionId: node.executionId,
      stage: index,
      step: this.props.execution.stageSummaries[index].firstActiveStage
    };

    if (stateService.includes('**.execution', params)) {
      if (!this.props.standalone) {
        stateService.go('^');
      }
    } else {
      if (stateService.current.name.includes('.executions.execution') || this.props.standalone) {
        stateService.go('.', params);
      } else {
        stateService.go('.execution', params);
      }
    }
  }

  public getUrl(): string {
    let url = $location.absUrl();
    if (!this.props.standalone) {
      url = url.replace('/executions', '/executions/details');
    }
    return url;
  }

  public deleteExecution(): void {
    confirmationModalService.confirm({
      header: 'Really delete execution?',
      buttonText: 'Delete',
      body: '<p>This will permanently delete the execution history.</p>',
      submitMethod: () => executionService.deleteExecution(this.props.application, this.props.execution.id).then( () => {
        if (this.props.standalone) {
          stateService.go('^.^.executions');
        }
      })
    });
  };

  public cancelExecution(): void {
    const hasDeployStage = this.props.execution.stages && this.props.execution.stages.some(stage => stage.type === 'deploy' || stage.type === 'cloneServerGroup');
    cancelModalService.confirm({
      header: `Really stop execution of ${this.props.execution.name}?`,
      buttonText: `Stop running ${this.props.execution.name}`,
      forceable: this.props.execution.executionEngine !== 'v1',
      body: hasDeployStage ? '<b>Note:</b> Any deployments that have begun will continue and need to be cleaned up manually.' : null,
      submitMethod: (reason, force) => executionService.cancelExecution(this.props.application, this.props.execution.id, force, reason)
    });
  }

  public pauseExecution(): void {
    confirmationModalService.confirm({
        header: 'Really pause execution?',
        buttonText: 'Pause',
        body: '<p>This will pause the pipeline for up to 72 hours.</p><p>After 72 hours the pipeline will automatically timeout and fail.</p>',
        submitMethod: () => executionService.pauseExecution(this.props.application, this.props.execution.id)
    });
  }

  public resumeExecution(): void {
    confirmationModalService.confirm({
        header: 'Really resume execution?',
        buttonText: 'Resume',
        submitMethod: () => executionService.resumeExecution(this.props.application, this.props.execution.id)
    });
  }

  public componentDidMount(): void {
    this.mounted = true;
    this.applicationRefreshUnsubscribe = this.props.application.executions.onRefresh(null, () => { this.forceUpdate(); });
    this.runningTime = new OrchestratedItemRunningTime(this.props.execution, (time: number) => this.setState({ runningTimeInMs: time }));
  }

  public componentWillReceiveProps(nextProps: IExecutionProps): void {
    if (nextProps.execution !== this.props.execution) {
      this.setState({
        showingDetails: this.invalidateShowingDetails()
      });
    }
  }

  public componentWillUnmount(): void {
    this.mounted = false;
    this.stateChangeSuccessDeregister();
    this.runningTime.reset();
    if (this.applicationRefreshUnsubscribe) {
      this.applicationRefreshUnsubscribe();
      this.applicationRefreshUnsubscribe = undefined;
    }
    if (this.activeRefresher) {
      this.activeRefresher.unsubscribe();
    }
  }

  public render() {
    const accountLabels = this.props.execution.deploymentTargets.map((account) => (
      <AccountLabelColor key={account} account={account}></AccountLabelColor>
    ));

    const executionMarkerWidth = `${100 / this.props.execution.stageSummaries.length}%`;
    const executionMarkers = this.props.execution.stageSummaries.map((stage, index) => (
      <ExecutionMarker key={stage.refId}
                       stage={stage}
                       onClick={() => this.toggleDetails({executionId: this.props.execution.id, index: index})}
                       active={this.isActive(index)}
                       width={executionMarkerWidth}></ExecutionMarker>
    ));

    return (
      <div className={`execution ${this.state.showingDetails ? 'show-details' : 'details-hidden'}`} id={`execution-${this.props.execution.id}`}>
        <div className={`execution-overview group-by-${this.state.sortFilter.groupBy}`}>
          { this.state.sortFilter.groupBy !== 'name' && (
            <h4 className="execution-name">
              {accountLabels}
              {this.props.execution.name}
            </h4>
          )}
          <ExecutionStatus execution={this.props.execution}
                           toggleDetails={(node) => this.toggleDetails(node)}
                           showingDetails={this.state.showingDetails}
                           standalone={this.props.standalone}>
          </ExecutionStatus>
          <div className="execution-bar">
            <div className="stages">
              {executionMarkers}
              { !this.props.execution.stageSummaries.length && (
                <div className="text-center">
                  No stages found.
                  <a onClick={() => ReactGA.event({category: 'Pipeline', action: 'Execution source clicked (no stages found)'})}
                    target="_blank"
                    href={this.state.pipelinesUrl + this.props.execution.id}>Source</a>
                </div>
              )}
            </div>
            <div className="execution-summary">
              Status: <span className={`status execution-status execution-status-${this.props.execution.status.toLowerCase()}`}>{this.props.execution.status}</span>
              { this.props.execution.cancellationReason && (<Tooltip value={this.props.execution.cancellationReason}><span className="glyphicon glyphicon-info-sign"></span></Tooltip>) }
              { this.props.execution.canceledBy && (<span> by {this.props.execution.canceledBy} &mdash; {timestamp(this.props.execution.endTime)}</span>) }
              { this.state.restartDetails && (<span> Restarted by {this.state.restartDetails.restartedBy} &mdash; {timestamp(this.state.restartDetails.restartTime)}</span>) }
              <span className="pull-right">Duration: {duration(this.props.execution.runningTimeInMs)}</span>
            </div>
          </div>
          <div className="execution-actions">
            { this.props.execution.isRunning && (
              <Tooltip value="Pause execution">
                <a className="clickable"
                   onClick={(event) => {
                    ReactGA.event({category: 'Pipeline', action: 'Execution pause clicked'});
                    this.pauseExecution();
                    event.stopPropagation();
                  }}>
                  <span className="glyphicon glyphicon-pause"></span>
                </a>
              </Tooltip>
            )}
            { this.props.execution.isPaused && (
              <Tooltip value="Resume execution">
                <a className="clickable"
                   onClick={(event) => {
                    ReactGA.event({category: 'Pipeline', action: 'Execution resume clicked'});
                    this.resumeExecution();
                    event.stopPropagation();
                  }}>
                  <span className="glyphicon glyphicon-play"></span>
                </a>
              </Tooltip>
            )}
            { !this.props.execution.isActive && (
              <Tooltip value="Delete execution">
                <a className="clickable"
                   onClick={(event) => {
                    ReactGA.event({category: 'Pipeline', action: 'Execution delete clicked'});
                    this.deleteExecution();
                    event.stopPropagation();
                  }}>
                  <span className="glyphicon glyphicon-trash"></span>
                </a>
              </Tooltip>
            )}
            { this.props.execution.isActive && (
              <Tooltip value="Cancel execution">
                <a className="clickable"
                   onClick={(event) => {
                    ReactGA.event({category: 'Pipeline', action: 'Execution cancel clicked'});
                    this.cancelExecution();
                    event.stopPropagation();
                }}>
                  <span className="glyphicon glyphicon-remove-circle"></span>
                </a>
              </Tooltip>
            )}
          </div>
        </div>
        { this.state.showingDetails && (
          <div className="execution-graph">
            { this.props.execution.parallel && (
              <PipelineGraph execution={this.props.execution}
                             onNodeClick={(node) => this.toggleDetails(node)}
                             viewState={this.state.viewState}>
              </PipelineGraph>
            )}
          </div>
        )}
        { this.state.showingDetails && (
          <div className="execution-details-container">
            <ExecutionDetails execution={this.props.execution} application={this.props.application} standalone={this.props.standalone}>
            </ExecutionDetails>
            <div className="permalinks">
              <div className="permalinks-content">
                <a onClick={() => ReactGA.event({category: 'Pipeline', action: 'Execution source clicked'})}
                  target="_blank"
                  href={this.state.pipelinesUrl + this.props.execution.id}>Source</a>
                { ' | ' }
                <a onClick={() => ReactGA.event({category: 'Pipeline', action: 'Permalink clicked'}) }
                   href={this.getUrl()}>Permalink</a>
                <CopyToClipboard text={this.getUrl()} toolTip="Copy permalink to clipboard"></CopyToClipboard>
              </div>
            </div>
          </div>
        )}
      </div>
    );
  }
}
