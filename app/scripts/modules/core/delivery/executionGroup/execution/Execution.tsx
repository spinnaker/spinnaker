import * as React from 'react';
import * as ReactGA from 'react-ga';
import { clone } from 'lodash';
import { $location } from 'ngimport';
import { Subscription } from 'rxjs/Subscription';
import autoBindMethods from 'class-autobind-decorator';

import { Application } from 'core/application/application.model';
import { IExecution , IRestartDetails } from 'core/domain';
import { IExecutionViewState } from 'core/pipeline/config/graph/pipelineGraph.service';
import { IPipelineNode } from 'core/pipeline/config/graph/pipelineGraph.service';
import { OrchestratedItemRunningTime } from './OrchestratedItemRunningTime';
import { SETTINGS } from 'core/config/settings';
import { $state, $stateParams } from 'core/uirouter';
import { cancelModalService } from 'core/cancelModal/cancelModal.service';
import { confirmationModalService } from 'core/confirmationModal/confirmationModal.service';
import { duration, timestamp } from 'core/utils/timeFormatters';
import { executionFilterModel } from 'core/delivery/filter/executionFilter.model';
import { executionService } from 'core/delivery/service/execution.service';
import { schedulerFactory } from 'core/scheduler/scheduler.factory';
import { stateEvents } from 'core/state.events';

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
  standalone?: boolean;
}

interface IExecutionState {
  showingDetails: boolean;
  pipelinesUrl: string;
  viewState: IExecutionViewState;
  sortFilter: any;
  restartDetails: IRestartDetails;
  runningTimeInMs: number;
}

@autoBindMethods
export class Execution extends React.Component<IExecutionProps, IExecutionState> {
  private activeRefresher: any;
  private stateChangeSuccessSubscription: Subscription;
  private runningTime: OrchestratedItemRunningTime;

  // Since executionService.getExecution is a promise, we cannot short circuit the .then()
  // callback, set a mounted flag to handle inside the then callback.
  // TODO: Convert executionService.updateExecution() to an Observable and subscribe/unsubscribe
  private mounted: boolean;

  constructor(props: IExecutionProps) {
    super(props);

    const initialViewState = {
      activeStageId: Number($stateParams.stage),
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
    newViewState.activeStageId = Number($stateParams.stage);
    newViewState.executionId = $stateParams.executionId;
    this.setState({
      viewState: newViewState,
      showingDetails: this.invalidateShowingDetails()
    });
  }

  private invalidateShowingDetails(): boolean {
    return (this.props.standalone === true || (this.props.execution.id === $stateParams.executionId &&
      $state.includes('**.execution.**')));
  }

  public isActive(stageIndex: number): boolean {
    return this.state.showingDetails && Number($stateParams.stage) === stageIndex;
  }

  public toggleDetails(stageIndex?: number): void {
    if (this.props.execution.id === $state.params.executionId && $state.current.name.includes('.executions.execution') && stageIndex === undefined) {
      $state.go('^');
      return;
    }
    const index = stageIndex || 0;
    const stageSummary = this.props.execution.stageSummaries[index] || { firstActiveStage: 0 };
    const params = {
      executionId: this.props.execution.id,
      stage: index,
      step: stageSummary.firstActiveStage
    };

    if ($state.includes('**.execution', params)) {
      if (!this.props.standalone) {
        $state.go('^');
      }
    } else {
      if ($state.current.name.includes('.executions.execution') || this.props.standalone) {
        $state.go('.', params);
      } else {
        $state.go('.execution', params);
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
          $state.go('^.^.executions');
        }
      })
    });
  }

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
    this.runningTime = new OrchestratedItemRunningTime(this.props.execution, (time: number) => this.setState({ runningTimeInMs: time }));
    this.stateChangeSuccessSubscription = stateEvents.stateChangeSuccess.subscribe(() => this.updateViewStateDetails());
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
    this.runningTime.reset();
    if (this.activeRefresher) {
      this.activeRefresher.unsubscribe();
    }
    if (this.stateChangeSuccessSubscription) {
      this.stateChangeSuccessSubscription.unsubscribe();
    }
  }

  private handleNodeClick(node: IPipelineNode): void {
    this.toggleDetails(node.index);
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
    const accountLabels = this.props.execution.deploymentTargets.map((account) => (
      <AccountLabelColor key={account} account={account}/>
    ));

    const executionMarkerWidth = `${100 / this.props.execution.stageSummaries.length}%`;
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

    return (
      <div className={`execution ${this.state.showingDetails ? 'show-details' : 'details-hidden'}`} id={`execution-${this.props.execution.id}`}>
        <div className={`execution-overview group-by-${this.state.sortFilter.groupBy}`}>
          { this.state.sortFilter.groupBy !== 'name' && (
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
            { this.props.execution.parallel && (
              <PipelineGraph
                execution={this.props.execution}
                onNodeClick={this.handleNodeClick}
                viewState={this.state.viewState}
              />
            )}
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
