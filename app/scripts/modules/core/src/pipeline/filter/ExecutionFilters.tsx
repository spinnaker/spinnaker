import { IPromise } from 'angular';
import React from 'react';
import ReactGA from 'react-ga';
import { get, isEmpty, orderBy, uniq, isEqual } from 'lodash';
import { Debounce } from 'lodash-decorators';
import classNames from 'classnames';
import { SortableContainer, SortableElement, SortableHandle, arrayMove, SortEnd } from 'react-sortable-hoc';
import { Subscription } from 'rxjs';

import { Application } from 'core/application';
import { FilterSection } from 'core/cluster/filter/FilterSection';
import { IFilterTag } from 'core/filterModel';
import { IExecution, IPipeline } from 'core/domain';
import { PipelineConfigService } from '../config/services/PipelineConfigService';
import { ReactInjector } from 'core/reactShims';
import { ExecutionState } from 'core/state';
import { ExecutionFilterService } from './executionFilter.service';

import './executionFilters.less';

export interface IExecutionFiltersProps {
  application: Application;
  setReloadingForFilters: (reloadingForFilters: boolean) => void;
}

export interface IExecutionFiltersState {
  pipelineNames: string[];
  strategyNames: string[];
  pipelineReorderEnabled: boolean;
  tags: IFilterTag[];
}

const DragHandle = SortableHandle(() => (
  <span className="pipeline-drag-handle clickable glyphicon glyphicon-resize-vertical" />
));

export class ExecutionFilters extends React.Component<IExecutionFiltersProps, IExecutionFiltersState> {
  private executionsRefreshUnsubscribe: () => void;
  private groupsUpdatedSubscription: Subscription;
  private locationChangeUnsubscribe: Function;
  private pipelineConfigsRefreshUnsubscribe: () => void;

  constructor(props: IExecutionFiltersProps) {
    super(props);

    this.state = {
      pipelineNames: this.getPipelineNames(false),
      strategyNames: this.getPipelineNames(true),
      pipelineReorderEnabled: false,
      tags: ExecutionState.filterModel.asFilterModel.tags,
    };
  }

  public componentDidMount(): void {
    const { application } = this.props;

    this.executionsRefreshUnsubscribe = application.executions.onRefresh(null, () => {
      ExecutionFilterService.updateExecutionGroups(this.props.application);
    });
    this.groupsUpdatedSubscription = ExecutionFilterService.groupsUpdatedStream.subscribe(() =>
      this.setState({ tags: ExecutionState.filterModel.asFilterModel.tags }),
    );
    this.pipelineConfigsRefreshUnsubscribe = application.pipelineConfigs.onRefresh(null, () => {
      this.refreshPipelines();
    });

    this.initialize();
    this.locationChangeUnsubscribe = ReactInjector.$uiRouter.transitionService.onSuccess({}, () => {
      ExecutionState.filterModel.asFilterModel.activate();
      ExecutionFilterService.updateExecutionGroups(application);
    });
  }

  private enablePipelineReorder = (): void => {
    this.setState({ pipelineReorderEnabled: true });
    ReactGA.event({ category: 'Pipelines', action: 'Filter: reorder' });
  };

  private disablePipelineReorder = (): void => {
    this.setState({ pipelineReorderEnabled: false });
    ReactGA.event({ category: 'Pipelines', action: 'Filter: stop reorder' });
  };

  private updateExecutionGroups(): void {
    ExecutionState.filterModel.asFilterModel.applyParamsToUrl();
    ExecutionFilterService.updateExecutionGroups(this.props.application);
  }

  private refreshExecutions = (): void => {
    ExecutionState.filterModel.asFilterModel.applyParamsToUrl();
    this.props.setReloadingForFilters(true);
    const dataSource = this.props.application.getDataSource('executions');
    dataSource.refresh(true).then(() => {
      ExecutionFilterService.updateExecutionGroups(this.props.application);
      this.props.setReloadingForFilters(false);
    });
  };

  private clearFilters = (): void => {
    ReactGA.event({ category: 'Pipelines', action: `Filter: clear all (side nav)` });
    ExecutionFilterService.clearFilters();
    this.refreshExecutions();
  };

  private getPipelineNames(strategy: boolean): string[] {
    const { application } = this.props;
    if (application.pipelineConfigs.loadFailure) {
      return [];
    }
    const source = strategy ? 'strategyConfigs' : 'pipelineConfigs';
    const otherSource = strategy ? 'pipelineConfigs' : 'strategyConfigs';
    const configs = get(application, `${source}.data`, []);
    const otherConfigs = get(application, `${otherSource}.data`, []);
    const allConfigIds = configs.concat(otherConfigs).map(c => c.id);
    // assume executions which don't have a match by pipelineConfigId are regular executions, not strategies
    const unmatchedExecutions = strategy
      ? []
      : application.executions.data.filter((e: IExecution) => !allConfigIds.includes(e.pipelineConfigId));
    const allOptions = orderBy(configs, ['strategy', 'index'], ['desc', 'asc'])
      .concat(unmatchedExecutions)
      .filter((option: any) => option && option.name)
      .map((option: any) => option.name);
    return uniq(allOptions);
  }

  private refreshPipelines(): void {
    const { pipelineNames, strategyNames } = this.state;
    const newPipelineNames = this.getPipelineNames(false);
    const newStrategyNames = this.getPipelineNames(true);
    if (!isEqual(pipelineNames, newPipelineNames) || !isEqual(strategyNames, newStrategyNames)) {
      this.setState({ pipelineNames: newPipelineNames, strategyNames: newStrategyNames });
      this.initialize();
    }
  }

  private initialize(): void {
    const { application } = this.props;
    if (application.pipelineConfigs.loadFailure) {
      return;
    }
    this.updateExecutionGroups();
  }

  public componentWillUnmount(): void {
    this.groupsUpdatedSubscription.unsubscribe();
    this.locationChangeUnsubscribe();
    this.executionsRefreshUnsubscribe();
    this.pipelineConfigsRefreshUnsubscribe();
  }

  @Debounce(300)
  private updateFilterSearch(searchString: string): void {
    const sortFilter = ExecutionState.filterModel.asFilterModel.sortFilter;
    sortFilter.filter = searchString;
    ReactGA.event({ category: 'Pipelines', action: 'Filter: search', label: sortFilter.filter });
    this.updateExecutionGroups();
  }

  private searchFieldUpdated = (event: React.FormEvent<HTMLInputElement>): void => {
    this.updateFilterSearch(event.currentTarget.value);
  };

  private updatePipelines(idsToUpdatedIndices: { [key: string]: number }): IPromise<void> {
    return PipelineConfigService.reorderPipelines(this.props.application.name, idsToUpdatedIndices, false);
  }

  private handleSortEnd = (sortEnd: SortEnd): void => {
    const pipelineNames = arrayMove(this.state.pipelineNames, sortEnd.oldIndex, sortEnd.newIndex);
    const { application } = this.props;
    ReactGA.event({ category: 'Pipelines', action: 'Reordered pipeline' });
    const idsToUpdatedIndices: { [key: string]: number } = {};
    application.pipelineConfigs.data.forEach((pipeline: IPipeline) => {
      const newIndex = pipelineNames.indexOf(pipeline.name);
      if (pipeline.index !== newIndex) {
        pipeline.index = newIndex;
        idsToUpdatedIndices[pipeline.id] = newIndex;
      }
    });
    if (!isEmpty(idsToUpdatedIndices)) {
      this.updatePipelines(idsToUpdatedIndices);
      this.refreshPipelines();
    }
  };

  public render() {
    const { pipelineNames, strategyNames, pipelineReorderEnabled, tags } = this.state;

    return (
      <div className="execution-filters">
        <div className="filters-content">
          <div className="heading">
            <span
              onClick={this.clearFilters}
              className="btn btn-default btn-xs"
              style={{ visibility: tags.length > 0 ? 'visible' : 'hidden' }}
            >
              Clear All
            </span>

            <FilterSection heading="Search" expanded={true} helpKey="executions.search">
              <form className="form-horizontal" role="form">
                <div className="form-group nav-search">
                  <input
                    type="search"
                    className="form-control input-sm"
                    onBlur={this.searchFieldUpdated}
                    onChange={this.searchFieldUpdated}
                    style={{ width: '85%', display: 'inline-block' }}
                  />
                </div>
              </form>
            </FilterSection>
          </div>
          <div className="content">
            <FilterSection heading="Pipelines" expanded={true}>
              <div className="form">
                <Pipelines
                  names={pipelineNames}
                  tags={tags}
                  dragEnabled={pipelineReorderEnabled}
                  update={this.refreshExecutions}
                  onSortEnd={this.handleSortEnd}
                />
                {pipelineNames.length > 0 && (
                  <div>
                    {!pipelineReorderEnabled && (
                      <a className="btn btn-xs btn-default clickable" onClick={this.enablePipelineReorder}>
                        Reorder Pipelines
                      </a>
                    )}
                    {pipelineReorderEnabled && (
                      <a className="btn btn-xs btn-default clickable" onClick={this.disablePipelineReorder}>
                        Done
                      </a>
                    )}
                  </div>
                )}
              </div>
            </FilterSection>

            {strategyNames.length > 0 && (
              <FilterSection heading="Strategies" expanded={true}>
                <div className="form">
                  <Pipelines
                    names={strategyNames}
                    tags={tags}
                    dragEnabled={false}
                    update={this.refreshExecutions}
                    onSortEnd={this.handleSortEnd}
                  />
                </div>
              </FilterSection>
            )}

            <FilterSection heading="Status" expanded={true}>
              <div className="form">
                <FilterStatus status="RUNNING" label="Running" refresh={this.refreshExecutions} />
                <FilterStatus status="TERMINAL" label="Terminal" refresh={this.refreshExecutions} />
                <FilterStatus status="SUCCEEDED" label="Succeeded" refresh={this.refreshExecutions} />
                <FilterStatus status="NOT_STARTED" label="Not Started" refresh={this.refreshExecutions} />
                <FilterStatus status="CANCELED" label="Canceled" refresh={this.refreshExecutions} />
                <FilterStatus status="STOPPED" label="Stopped" refresh={this.refreshExecutions} />
                <FilterStatus status="PAUSED" label="Paused" refresh={this.refreshExecutions} />
                <FilterStatus status="BUFFERED" label="Buffered" refresh={this.refreshExecutions} />
              </div>
            </FilterSection>
          </div>
        </div>
      </div>
    );
  }
}

const FilterCheckbox = (props: {
  tag: IFilterTag;
  pipeline: string;
  visible: boolean;
  update: () => void;
}): JSX.Element => {
  const { pipeline, tag, update, visible } = props;
  const sortFilter = ExecutionState.filterModel.asFilterModel.sortFilter;
  const changeHandler = () => {
    ReactGA.event({ category: 'Pipelines', action: 'Filter: pipeline', label: pipeline });
    if (tag) {
      tag.clear();
    } else {
      sortFilter.pipeline[pipeline] = true;
    }
    update();
  };
  return (
    <input
      type="checkbox"
      style={{ visibility: visible ? 'visible' : 'hidden' }}
      disabled={!visible}
      checked={get(sortFilter, ['pipeline', pipeline], false)}
      onChange={changeHandler}
    />
  );
};

const Pipeline = SortableElement(
  (props: { tag: IFilterTag; pipeline: string; dragEnabled: boolean; update: () => void }) => (
    <div className={classNames('checkbox sortable', { 'disable-user-select': props.dragEnabled })}>
      <div>
        <label>
          {props.dragEnabled && <DragHandle />}
          <FilterCheckbox
            pipeline={props.pipeline}
            tag={props.tag}
            visible={!props.dragEnabled}
            update={props.update}
          />
          {props.pipeline}
        </label>
      </div>
    </div>
  ),
);

const Pipelines = SortableContainer(
  (props: { names: string[]; tags: IFilterTag[]; dragEnabled: boolean; update: () => void }) => (
    <div>
      {props.names.map((pipeline, index) => {
        const tag = props.tags.find(t => t.key === 'pipeline' && t.value === pipeline);

        return (
          <Pipeline
            key={pipeline}
            tag={tag}
            index={index}
            pipeline={pipeline}
            disabled={!props.dragEnabled}
            dragEnabled={props.dragEnabled}
            update={props.update}
          />
        );
      })}
    </div>
  ),
);

const FilterStatus = (props: { status: string; label: string; refresh: () => void }): JSX.Element => {
  const sortFilter = ExecutionState.filterModel.asFilterModel.sortFilter;
  const changed = () => {
    ReactGA.event({ category: 'Pipelines', action: 'Filter: status', label: props.label.toUpperCase() });
    sortFilter.status[props.status] = !sortFilter.status[props.status];
    props.refresh();
  };
  return (
    <div className="checkbox">
      <label>
        <input type="checkbox" checked={sortFilter.status[props.status] || false} onChange={changed} />
        {props.label}
      </label>
    </div>
  );
};
