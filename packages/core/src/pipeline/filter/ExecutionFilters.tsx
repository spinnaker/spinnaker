import classNames from 'classnames';
import { flatten, get, isEmpty, isEqual, orderBy, uniq } from 'lodash';
import { Debounce } from 'lodash-decorators';
import React from 'react';
import { arrayMove, SortableContainer, SortableElement, SortableHandle, SortEnd } from 'react-sortable-hoc';
import { Subscription } from 'rxjs';

import { Application } from '../../application';
import { FilterSearch } from '../../cluster/filter/FilterSearch';
import { FilterSection } from '../../cluster/filter/FilterSection';
import { PipelineConfigService } from '../config/services/PipelineConfigService';
import { IExecution, IPipeline, IPipelineTag } from '../../domain';
import { ExecutionFilterService } from './executionFilter.service';
import { IFilterTag } from '../../filterModel';
import { ReactInjector } from '../../reactShims';
import { ExecutionState } from '../../state';
import { logger } from '../../utils';

import './executionFilters.less';

export interface IExecutionFiltersProps {
  application: Application;
  setReloadingForFilters: (reloadingForFilters: boolean) => void;
}

export interface IExecutionFiltersState {
  pipelineNames: string[];
  strategyNames: string[];
  pipelineTags: IOrderedPipelineTagFilters;
  pipelineReorderEnabled: boolean;
  searchString: string;
  tags: IFilterTag[];
}

interface IOrderedPipelineTagFilters {
  names: string[];
  values: { [key: string]: string[] };
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

    const searchString = ExecutionState.filterModel.asFilterModel.sortFilter.filter;
    this.state = {
      pipelineNames: this.getPipelineNames(false).filter((pipelineName) =>
        searchString ? pipelineName.toLocaleLowerCase().includes(searchString.toLocaleLowerCase()) : true,
      ),
      strategyNames: this.getPipelineNames(true),
      pipelineTags: this.getPipelineTags(),
      pipelineReorderEnabled: false,
      searchString,
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
    logger.log({ category: 'Pipelines', action: 'Filter: reorder' });
  };

  private disablePipelineReorder = (): void => {
    this.setState({ pipelineReorderEnabled: false });
    logger.log({ category: 'Pipelines', action: 'Filter: stop reorder' });
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

  private getPipelineTags(): IOrderedPipelineTagFilters {
    const pipelineConfigs: IPipeline[] = this.props.application.pipelineConfigs.loadFailure
      ? []
      : get(this.props.application, 'pipelineConfigs.data', []);

    // Since pipeline.tags is an array of tags, we'll need to flatten
    const extractedPipelineTags: IPipelineTag[] = flatten(
      pipelineConfigs.filter((pipeline) => pipeline.tags).map((pipeline) => pipeline.tags),
    );

    return extractedPipelineTags.reduce(
      (pipelineTags: IOrderedPipelineTagFilters, { name, value }) => {
        if (!pipelineTags.names.includes(name)) {
          pipelineTags.names.push(name);
        }
        pipelineTags.values[name] = pipelineTags.values[name] || [];
        if (!pipelineTags.values[name].includes(value)) {
          pipelineTags.values[name].push(value);
        }
        return pipelineTags;
      },
      {
        names: [],
        values: {},
      },
    );
  }

  private getPipelineNames(strategy: boolean): string[] {
    const { application } = this.props;
    if (application.pipelineConfigs.loadFailure) {
      return [];
    }
    const source = strategy ? 'strategyConfigs' : 'pipelineConfigs';
    const otherSource = strategy ? 'pipelineConfigs' : 'strategyConfigs';
    const configs = get(application, `${source}.data`, []);
    const otherConfigs = get(application, `${otherSource}.data`, []);
    const allConfigIds = configs.concat(otherConfigs).map((c) => c.id);
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
    const { pipelineNames, strategyNames, searchString } = this.state;
    let newPipelineNames = this.getPipelineNames(false);
    if (searchString.length > 0) {
      newPipelineNames = newPipelineNames.filter((pipelineName) =>
        pipelineName.toLocaleLowerCase().includes(searchString.toLocaleLowerCase()),
      );
    }
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
    logger.log({ category: 'Pipelines', action: 'Filter: search', data: { label: sortFilter.filter } });
    this.updateExecutionGroups();
  }

  private searchFieldUpdated = (event: React.FormEvent<HTMLInputElement>): void => {
    this.setState({ searchString: event.currentTarget.value }, () => this.refreshPipelines());
    this.updateFilterSearch(event.currentTarget.value);
  };

  private updatePipelines(idsToUpdatedIndices: { [key: string]: number }): PromiseLike<void> {
    return PipelineConfigService.reorderPipelines(this.props.application.name, idsToUpdatedIndices, false);
  }

  // For ReactSortable
  private handleSortEnd = (sortEnd: SortEnd): void => {
    const pipelineNames = arrayMove(this.state.pipelineNames, sortEnd.oldIndex, sortEnd.newIndex);
    this.applyNewPipelineSortOrder(pipelineNames);
  };

  private sortAlphabetically = () => {
    const pipelineNames = this.state.pipelineNames.slice().sort();
    this.applyNewPipelineSortOrder(pipelineNames);
  };

  private applyNewPipelineSortOrder = (pipelineNames: string[]): void => {
    const { application } = this.props;
    logger.log({ category: 'Pipelines', action: 'Reordered pipeline' });
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
    const { pipelineNames, searchString, strategyNames, pipelineReorderEnabled, tags, pipelineTags } = this.state;
    const { sortFilter } = ExecutionState.filterModel.asFilterModel;

    return (
      <div className="execution-filters">
        <div className="filters-content">
          <div className="heading">
            <FilterSearch
              helpKey="executions.search"
              value={searchString}
              onBlur={this.searchFieldUpdated}
              onSearchChange={this.searchFieldUpdated}
            />
          </div>
          <div className="content">
            <PipelineTagFilters pipelineTags={pipelineTags} refresh={this.refreshExecutions} />
            <FilterSection heading="Pipelines" expanded={true}>
              <div className="form">
                {pipelineReorderEnabled && (
                  <a className="btn btn-xs btn-default clickable margin-left-md" onClick={this.sortAlphabetically}>
                    Sort alphabetically
                  </a>
                )}

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
                <FilterStatus
                  status="RUNNING"
                  label="Running"
                  refresh={this.refreshExecutions}
                  disabled={sortFilter.awaitingJudgement}
                />
                <FilterAwaitingJudgement refresh={this.refreshExecutions} />
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
    logger.log({ category: 'Pipelines', action: 'Filter: pipeline', data: { label: pipeline } });
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
        const tag = props.tags.find((t) => t.key === 'pipeline' && t.value === pipeline);

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

const PipelineTagFilters = (props: { pipelineTags: IOrderedPipelineTagFilters; refresh: () => void }): JSX.Element => (
  <>
    {props.pipelineTags.names.map((name) => (
      <FilterSection key={name} heading={name} expanded={true}>
        {(props.pipelineTags.values[name] || []).map((value) => (
          <PipelineTagFilter key={value} group={name} value={value} refresh={props.refresh} />
        ))}
      </FilterSection>
    ))}
  </>
);

const PipelineTagFilter = (props: { group: string; value: string; refresh: () => void }): JSX.Element => {
  const sortFilter = ExecutionState.filterModel.asFilterModel.sortFilter;
  const { group, value, refresh } = props;
  const key = `${encodeURIComponent(group)}:${encodeURIComponent(value)}`;
  const changed = () => {
    sortFilter.tags[key] = !sortFilter.tags[key];
    refresh();
  };
  return (
    <div className="checkbox">
      <label>
        <input type="checkbox" checked={sortFilter.tags[key] || false} onChange={changed} />
        {value}
      </label>
    </div>
  );
};

const FilterStatus = (props: {
  status: string;
  disabled?: boolean;
  label: string;
  refresh: () => void;
}): JSX.Element => {
  const sortFilter = ExecutionState.filterModel.asFilterModel.sortFilter;
  const changed = () => {
    logger.log({ category: 'Pipelines', action: 'Filter: status', data: { label: props.label.toUpperCase() } });
    sortFilter.status[props.status] = !sortFilter.status[props.status];
    props.refresh();
  };
  return (
    <div className="checkbox">
      <label>
        <input
          type="checkbox"
          checked={sortFilter.status[props.status] || false}
          disabled={!!props.disabled}
          onChange={changed}
        />
        {props.label}
      </label>
    </div>
  );
};

const FilterAwaitingJudgement = (props: { refresh: () => void }): JSX.Element => {
  const { sortFilter } = ExecutionState.filterModel.asFilterModel;
  const label = 'Awaiting Judgement';
  const changed = () => {
    logger.log({ category: 'Pipelines', action: 'Filter: status', data: { label } });
    sortFilter.awaitingJudgement = !sortFilter.awaitingJudgement;
    if (sortFilter.awaitingJudgement) {
      sortFilter.status['RUNNING'] = true;
    }
    props.refresh();
  };
  const checked = !!sortFilter.awaitingJudgement;
  return (
    <div className="checkbox">
      <label>
        <input type="checkbox" checked={checked} onChange={changed} /> {label}
      </label>
    </div>
  );
};
