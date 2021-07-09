import { get } from 'lodash';
import { $q } from 'ngimport';
import React from 'react';
import { Subscription } from 'rxjs';

import { Application } from '../../application';
import { CreatePipeline } from '../config/CreatePipeline';
import { CreatePipelineButton } from '../create/CreatePipelineButton';
import { IExecution, IPipeline, IPipelineCommand } from '../../domain';
import { ExecutionGroups } from './executionGroup/ExecutionGroups';
import { ExecutionFilters } from '../filter/ExecutionFilters';
import { ExecutionFilterService } from '../filter/executionFilter.service';
import { FilterCollapse, FilterTags, IFilterTag, ISortFilter } from '../../filterModel';
import { ManualExecutionModal } from '../manualExecution';
import { Overridable } from '../../overrideRegistry';
import { Tooltip } from '../../presentation/Tooltip';
import { ReactInjector } from '../../reactShims';
import { SchedulerFactory } from '../../scheduler';
import { IScheduler } from '../../scheduler/SchedulerFactory';
import { ExecutionState } from '../../state';
import { logger } from '../../utils';
import { IRetryablePromise } from '../../utils/retryablePromise';
import { Spinner } from '../../widgets/spinners/Spinner';

import './executions.less';

export interface IExecutionsProps {
  app: Application;
}

export interface IExecutionsState {
  initializationError?: boolean;
  filtersExpanded: boolean;
  loading: boolean;
  poll: IRetryablePromise<any>;
  sortFilter: ISortFilter;
  tags: IFilterTag[];
  triggeringExecution: boolean;
  reloadingForFilters: boolean;
}

// This Set ensures we only forward once from .executions to .executionDetails for an aged out execution
const forwardedExecutions = new Set();
// This ensures we only forward to permalink on landing, not on future refreshes
let disableForwarding = false;

@Overridable('PipelineExecutions')
export class Executions extends React.Component<IExecutionsProps, IExecutionsState> {
  private executionsRefreshUnsubscribe: Function;
  private groupsUpdatedSubscription: Subscription;
  private insightFilterStateModel = ReactInjector.insightFilterStateModel;
  private activeRefresher: IScheduler;

  private filterCountOptions = [1, 2, 5, 10, 20, 30, 40, 50, 100, 200];

  constructor(props: IExecutionsProps) {
    super(props);

    this.state = {
      filtersExpanded: this.insightFilterStateModel.filtersExpanded,
      loading: true,
      poll: null,
      sortFilter: ExecutionState.filterModel.asFilterModel.sortFilter,
      tags: [],
      triggeringExecution: false,
      reloadingForFilters: false,
    };
  }

  private setReloadingForFilters = (reloadingForFilters: boolean) => {
    if (this.state.reloadingForFilters !== reloadingForFilters) {
      this.setState({ reloadingForFilters });
    }
  };

  private clearFilters = (): void => {
    ExecutionFilterService.clearFilters();
    this.updateExecutionGroups(true);
  };

  private forceUpdateExecutionGroups = (): void => {
    this.updateExecutionGroups(true);
  };

  private updateExecutionGroups(reload?: boolean): void {
    this.normalizeExecutionNames();
    const { app } = this.props;
    // updateExecutionGroups is debounced by 25ms, so we need to delay setting the loading flags a bit
    if (reload) {
      this.setReloadingForFilters(true);
      app.executions.refresh(true).then(() => {
        ExecutionFilterService.updateExecutionGroups(app);
        setTimeout(() => this.setReloadingForFilters(false), 50);
      });
    } else {
      ExecutionFilterService.updateExecutionGroups(app);
      this.groupsUpdated();
      setTimeout(() => {
        this.setState({ loading: false });
      }, 50);
    }
  }

  private groupsUpdated(): void {
    const newTags = ExecutionState.filterModel.asFilterModel.tags;
    const currentTags = this.state.tags;
    const areEqual = (t1: IFilterTag, t2: IFilterTag) =>
      t1.key === t2.key && t1.label === t2.label && t1.value === t2.value;
    const tagsChanged =
      newTags.length !== currentTags.length || newTags.some((t1) => !currentTags.some((t2) => areEqual(t1, t2)));
    if (tagsChanged) {
      this.setState({ tags: newTags });
    }
  }

  private dataInitializationFailure(): void {
    this.setState({ loading: false, initializationError: true });
  }

  private normalizeExecutionNames(): void {
    const { app } = this.props;
    if (app.executions.loadFailure) {
      this.dataInitializationFailure();
    }
    const executions = app.executions.data || [];
    const configurations: any[] = app.pipelineConfigs.data || [];
    executions.forEach((execution: any) => {
      if (execution.pipelineConfigId) {
        const configMatch = configurations.find((c: any) => c.id === execution.pipelineConfigId);
        if (configMatch) {
          execution.name = configMatch.name;
        }
      }
    });
  }

  private expand = (): void => {
    logger.log({ category: 'Pipelines', action: 'Expand All' });
    ExecutionState.filterModel.expandSubject.next(true);
  };

  private collapse = (): void => {
    logger.log({ category: 'Pipelines', action: 'Collapse All' });
    ExecutionState.filterModel.expandSubject.next(false);
  };

  private startPipeline(command: IPipelineCommand): PromiseLike<void> {
    const { executionService } = ReactInjector;
    this.setState({ triggeringExecution: true });
    return executionService
      .startAndMonitorPipeline(this.props.app, command.pipelineName, command.trigger)
      .then((monitor) => {
        this.setState({ poll: monitor });
        return monitor.promise;
      })
      .finally(() => {
        this.setState({ triggeringExecution: false });
      });
  }

  private startManualExecutionClicked = (): void => {
    this.triggerPipeline();
  };

  private triggerPipeline(pipeline: IPipeline = null): void {
    logger.log({ category: 'Pipelines', action: 'Trigger Pipeline (top level)' });
    ManualExecutionModal.show({
      pipeline: pipeline,
      application: this.props.app,
    })
      .then((command) => {
        this.startPipeline(command);
        this.clearManualExecutionParam();
      })
      .catch(() => this.clearManualExecutionParam());
  }

  private clearManualExecutionParam(): void {
    ReactInjector.$state.go('.', { startManualExecution: null }, { inherit: true, location: 'replace' });
  }

  private handleAgedOutExecutions(executionId: string, forwardToPermalink: boolean): void {
    const { $state, executionService } = ReactInjector;
    if (forwardToPermalink && executionId && !forwardedExecutions.has(executionId)) {
      // We only want to forward to permalink on initial load
      executionService.getExecution(executionId).then(() => {
        const detailsState = $state.current.name.replace('executions.execution', 'executionDetails.execution');
        const { stage, step, details } = $state.params;
        forwardedExecutions.add(executionId);
        $state.go(detailsState, { executionId, stage, step, details });
      });
    } else {
      // Handles the case where we already forwarded once and user navigated back, so do not forward again.
      $state.go('.^');
    }
  }

  public componentDidMount(): void {
    const { app } = this.props;
    if (ExecutionState.filterModel.mostRecentApplication !== app.name) {
      ExecutionState.filterModel.asFilterModel.groups = [];
      ExecutionState.filterModel.mostRecentApplication = app.name;
    }

    if (app.notFound || app.hasError) {
      return;
    }
    app.setActiveState(app.executions);
    app.executions.activate();
    app.pipelineConfigs.activate();
    this.activeRefresher = SchedulerFactory.createScheduler(5000);
    this.activeRefresher.subscribe(() => {
      app.getDataSource('runningExecutions').refresh();
    });

    this.groupsUpdatedSubscription = ExecutionFilterService.groupsUpdatedStream.subscribe(() => this.groupsUpdated());

    this.executionsRefreshUnsubscribe = app.executions.onRefresh(
      null,
      () => {
        this.normalizeExecutionNames();

        // if an execution was selected but is no longer present, navigate up
        const { $state } = ReactInjector;
        if ($state.params.executionId) {
          const executions: IExecution[] = app.executions.data;
          if (executions.every((e) => e.id !== $state.params.executionId)) {
            this.handleAgedOutExecutions($state.params.executionId, !disableForwarding);
          }
        }
        // After the very first refresh interval (landing), we do not want to forward the user to the permalink
        disableForwarding = true;
      },
      () => this.dataInitializationFailure(),
    );

    $q.all([app.executions.ready(), app.pipelineConfigs.ready()]).then(() => {
      this.updateExecutionGroups();
      const nameOrIdToStart = ReactInjector.$stateParams.startManualExecution;
      if (nameOrIdToStart) {
        const toStart = app.pipelineConfigs.data.find((p: IPipeline) => [p.id, p.name].includes(nameOrIdToStart));
        if (toStart) {
          this.triggerPipeline(toStart);
        } else {
          this.clearManualExecutionParam();
        }
      }
    });
  }

  public componentWillUnmount(): void {
    const { app } = this.props;
    app.setActiveState();
    app.executions.deactivate();
    app.pipelineConfigs.deactivate();
    this.executionsRefreshUnsubscribe();
    this.groupsUpdatedSubscription.unsubscribe();
    this.activeRefresher && this.activeRefresher.unsubscribe();
    this.state.poll && this.state.poll.cancel();
  }

  private toggleFilters = (): void => {
    const newState = !this.state.filtersExpanded;
    this.setState({ filtersExpanded: newState });
    this.insightFilterStateModel.pinFilters(newState);
  };

  private groupByChanged = (event: React.ChangeEvent<HTMLSelectElement>): void => {
    const value = event.target.value;
    logger.log({ category: 'Pipelines', action: 'Group By', data: { label: value } });
    this.state.sortFilter.groupBy = value;
    this.updateExecutionGroups();
  };

  private showCountChanged = (event: React.ChangeEvent<HTMLSelectElement>): void => {
    const value = event.target.value;
    this.state.sortFilter.count = parseInt(value, 10);
    logger.log({ category: 'Pipelines', action: 'Change Count', data: { label: value } });
    this.updateExecutionGroups(true);
  };

  private showDurationsChanged = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const checked = event.target.checked;
    // TODO: Since we treat sortFilter like a store, we can force the setState for now
    //       but we should eventually convert all the sortFilters to be a valid redux
    //       (or similar) store.
    this.state.sortFilter.showDurations = checked;
    this.setState({ sortFilter: this.state.sortFilter });
    logger.log({ category: 'Pipelines', action: 'Toggle Durations', data: { label: checked.toString() } });
  };

  public render(): React.ReactElement<Executions> {
    const { app } = this.props;
    const { filtersExpanded, loading, sortFilter, tags, triggeringExecution, reloadingForFilters } = this.state;

    const hasPipelines = !!(get(app, 'executions.data', []).length || get(app, 'pipelineConfigs.data', []).length);

    if (!app.notFound && !app.hasError) {
      if (!hasPipelines && !loading) {
        return (
          <div className="text-center full-width">
            <h3>No pipelines configured for this application.</h3>
            <h4>
              <CreatePipelineButton application={app} asLink={true} />
            </h4>
          </div>
        );
      }
      return (
        <div className="executions-section">
          {!loading && (
            <div onClick={this.toggleFilters}>
              <FilterCollapse />
            </div>
          )}
          <div className={`insight ${filtersExpanded ? 'filters-expanded' : 'filters-collapsed'}`}>
            {filtersExpanded && (
              <div className="nav ng-scope">
                {!loading && (
                  <ExecutionFilters application={app} setReloadingForFilters={this.setReloadingForFilters} />
                )}
              </div>
            )}
            <div
              className={`nav-content ng-scope ${sortFilter.showDurations ? 'show-durations' : ''}`}
              data-scroll-id="nav-content"
            >
              {!loading && (
                <div className="execution-groups-header">
                  <div className="form-group pull-right">
                    <a
                      className="btn btn-sm btn-primary clickable"
                      onClick={this.startManualExecutionClicked}
                      style={{ pointerEvents: triggeringExecution ? 'none' : 'auto' }}
                    >
                      {triggeringExecution && (
                        <span>
                          <Tooltip value="Starting Execution">
                            <span className="visible-md-inline visible-sm-inline">
                              <Spinner size="nano" />
                            </span>
                          </Tooltip>
                          <span className="visible-lg-inline">
                            <Spinner size="nano" />
                          </span>
                          <span className="visible-xl-inline">Starting Execution</span>
                          &hellip;
                        </span>
                      )}
                      {!triggeringExecution && (
                        <span>
                          <span className="glyphicon glyphicon-play visible-lg-inline" />
                          <Tooltip value="Start Manual Execution">
                            <span className="glyphicon glyphicon-play visible-md-inline visible-sm-inline" />
                          </Tooltip>
                          <span className="visible-xl-inline"> Start Manual Execution</span>
                        </span>
                      )}
                    </a>
                  </div>
                  <div className="pull-right">
                    <CreatePipeline application={app} />
                  </div>
                  <form className="form-inline" style={{ marginBottom: '5px' }}>
                    {sortFilter.groupBy && (
                      <div className="form-group" style={{ marginRight: '20px' }}>
                        <Tooltip value="expand all">
                          <a className="btn btn-xs btn-default clickable" onClick={this.expand}>
                            <span className="glyphicon glyphicon-plus" />
                          </a>
                        </Tooltip>
                        <Tooltip value="collapse all">
                          <a className="btn btn-xs btn-default clickable" onClick={this.collapse}>
                            <span className="glyphicon glyphicon-minus" />
                          </a>
                        </Tooltip>
                      </div>
                    )}
                    <div className="form-group">
                      <label>Group by</label>
                      <select
                        className="form-control input-sm"
                        value={sortFilter.groupBy}
                        onChange={this.groupByChanged}
                      >
                        <option value="none">None</option>
                        <option value="name">Pipeline</option>
                        <option value="timeBoundary">Time Boundary</option>
                      </select>
                    </div>
                    <div className="form-group">
                      <label>Show </label>
                      <select
                        className="form-control input-sm"
                        value={sortFilter.count}
                        onChange={this.showCountChanged}
                      >
                        {this.filterCountOptions.map((count) => (
                          <option key={count} value={count}>
                            {count}
                          </option>
                        ))}
                      </select>
                      <span> executions per pipeline</span>
                    </div>
                    <div className="form-group checkbox">
                      <label>
                        <input
                          type="checkbox"
                          checked={sortFilter.showDurations}
                          onChange={this.showDurationsChanged}
                        />{' '}
                        stage durations
                      </label>
                    </div>
                  </form>
                  <FilterTags
                    tags={tags}
                    tagCleared={this.forceUpdateExecutionGroups}
                    clearFilters={this.clearFilters}
                  />
                </div>
              )}
              {loading && (
                <div className="horizontal center middle spinner-container">
                  <Spinner size="medium" />
                </div>
              )}
              {reloadingForFilters && (
                <div className="text-center transition-overlay" style={{ marginLeft: '-25px' }} />
              )}
              {!loading && !hasPipelines && (
                <div className="text-center">
                  <h4>No pipelines configured for this application.</h4>
                </div>
              )}
              {app.executions.loadFailure && (
                <div className="text-center">
                  <h4>There was an error loading executions. We'll try again shortly.</h4>
                </div>
              )}
              {!loading && hasPipelines && <ExecutionGroups application={app} />}
            </div>
          </div>
        </div>
      );
    }
    return null;
  }
}
