import * as React from 'react';
import * as ReactGA from 'react-ga';
import { get, orderBy, uniq } from 'lodash';
import { BindAll, Debounce } from 'lodash-decorators';
import { $q } from 'ngimport';
import { SortableContainer, SortableElement, SortableHandle, arrayMove, SortEnd } from 'react-sortable-hoc';
import { Subscription } from 'rxjs';

import { Application } from 'core/application';
import { FilterSection } from 'core/cluster/filter/FilterSection';
import { IPipeline } from 'core/domain';
import { ReactInjector } from 'core/reactShims';

import './executionFilters.less';

export interface IExecutionFiltersProps {
  application: Application;
}

export interface IExecutionFiltersState {
  pipelineNames: string[];
  pipelineReorderEnabled: boolean;
  tags: any[];
}

const DragHandle = SortableHandle(() => (
  <span className="pipeline-drag-handle clickable glyphicon glyphicon-resize-vertical"/>
));

@BindAll()
export class ExecutionFilters extends React.Component<IExecutionFiltersProps, IExecutionFiltersState> {
  private executionsRefreshUnsubscribe: () => void;
  private groupsUpdatedSubscription: Subscription;
  private locationChangeUnsubscribe: Function;
  private pipelineConfigsRefreshUnsubscribe: () => void;

  constructor(props: IExecutionFiltersProps) {
    const { executionFilterModel } = ReactInjector;
    super(props);

    this.state = {
      pipelineNames: this.getPipelineNames(),
      pipelineReorderEnabled: false,
      tags: executionFilterModel.asFilterModel.tags,
    }
  }

  public componentDidMount(): void {
    const { application } = this.props;
    const { executionFilterModel, executionFilterService } = ReactInjector;

    this.executionsRefreshUnsubscribe = application.executions.onRefresh(null, () => { this.refreshPipelines(); });
    this.groupsUpdatedSubscription = executionFilterService.groupsUpdatedStream.subscribe(() => this.setState({ tags: executionFilterModel.asFilterModel.tags }));
    this.pipelineConfigsRefreshUnsubscribe = application.pipelineConfigs.onRefresh(null, () => { this.refreshPipelines(); });

    this.initialize();
    this.locationChangeUnsubscribe = ReactInjector.$uiRouter.transitionService.onSuccess({}, () => {
      executionFilterModel.asFilterModel.activate();
      executionFilterService.updateExecutionGroups(application);
    });
  }

  private enablePipelineReorder(): void {
    this.setState({ pipelineReorderEnabled: true });
    ReactGA.event({ category: 'Pipelines', action: 'Filter: reorder' });
  };

  private disablePipelineReorder(): void {
    this.setState({ pipelineReorderEnabled: false });
    ReactGA.event({ category: 'Pipelines', action: 'Filter: stop reorder' });
  };

  private updateExecutionGroups(): void {
    ReactInjector.executionFilterModel.asFilterModel.applyParamsToUrl();
    ReactInjector.executionFilterService.updateExecutionGroups(this.props.application);
  }

  private refreshExecutions(): void {
    ReactInjector.executionFilterModel.asFilterModel.applyParamsToUrl();
    this.props.application.executions.reloadingForFilters = true;
    this.props.application.executions.refresh(true);
  }

  private clearFilters(): void {
    ReactGA.event({ category: 'Pipelines', action: `Filter: clear all (side nav)` });
    ReactInjector.executionFilterService.clearFilters();
    this.refreshExecutions();
  }

  private getPipelineNames(): string[] {
    const { application } = this.props;
    if (application.pipelineConfigs.loadFailure) {
      return [];
    }
    const configs = get(application, 'pipelineConfigs.data', []).concat(get(application, 'strategyConfigs.data', []));
    const allOptions = orderBy(configs, ['strategy', 'index'], ['desc', 'asc'])
      .concat(application.executions.data)
      .filter((option: any) => option && option.name)
      .map((option: any) => option.name);
    return uniq(allOptions);
  }

  private refreshPipelines(): void {
    this.setState({ pipelineNames: this.getPipelineNames() });
    this.initialize();
  }

  private initialize(): void {
    const { application } = this.props;
    if (application.pipelineConfigs.loadFailure) {
      return;
    }
    this.updateExecutionGroups();
    application.executions.reloadingForFilters = false;
  }

  public componentWillUnmount(): void {
    this.groupsUpdatedSubscription.unsubscribe();
    this.locationChangeUnsubscribe();
    this.executionsRefreshUnsubscribe();
    this.pipelineConfigsRefreshUnsubscribe();
  }

  @Debounce(300)
  private updateFilterSearch(searchString: string): void {
    const sortFilter = ReactInjector.executionFilterModel.asFilterModel.sortFilter;
    sortFilter.filter = searchString;
    ReactGA.event({ category: 'Pipelines', action: 'Filter: search', label: sortFilter.filter });
    this.updateExecutionGroups();
  }

  private searchFieldUpdated(event: React.FormEvent<HTMLInputElement>): void {
    this.updateFilterSearch(event.currentTarget.value);
  }

  private updatePipelines(pipelines: IPipeline[]): void {
    $q.all(pipelines.map((pipeline) => ReactInjector.pipelineConfigService.savePipeline(pipeline)));
  };

  private handleSortEnd(sortEnd: SortEnd): void {
    const pipelineNames = arrayMove(this.state.pipelineNames, sortEnd.oldIndex, sortEnd.newIndex);
    const { application } = this.props;
    ReactGA.event({ category: 'Pipelines', action: 'Reordered pipeline' });
    const dirty: IPipeline[] = [];
    application.pipelineConfigs.data.concat(application.strategyConfigs.data).forEach((pipeline: IPipeline) => {
      const newIndex = pipelineNames.indexOf(pipeline.name);
      if (pipeline.index !== newIndex) {
        pipeline.index = newIndex;
        dirty.push(pipeline);
      }
    });
    this.updatePipelines(dirty);
    this.refreshPipelines();
  }

  public render() {
    const { pipelineNames, pipelineReorderEnabled, tags } = this.state;

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
                  dragEnabled={pipelineReorderEnabled}
                  update={this.refreshExecutions}
                  onSortEnd={this.handleSortEnd}
                />
                { pipelineNames.length && (
                  <div>
                    { !pipelineReorderEnabled && (
                      <a
                        className="btn btn-xs btn-default clickable"
                        onClick={this.enablePipelineReorder}
                      >Reorder Pipelines
                      </a>
                    )}
                    { pipelineReorderEnabled && (
                      <a
                        className="btn btn-xs btn-default clickable"
                        onClick={this.disablePipelineReorder}
                      >Done
                      </a>
                    )}
                  </div>
                )}
              </div>
            </FilterSection>

            <FilterSection heading="Status" expanded={true}>
              <div className="form">
                <FilterStatus status="RUNNING" label="Running" refresh={this.refreshExecutions}/>
                <FilterStatus status="TERMINAL" label="Terminal" refresh={this.refreshExecutions}/>
                <FilterStatus status="SUCCEEDED" label="Succeeded" refresh={this.refreshExecutions}/>
                <FilterStatus status="NOT_STARTED" label="Not Started" refresh={this.refreshExecutions}/>
                <FilterStatus status="CANCELED" label="Canceled" refresh={this.refreshExecutions}/>
                <FilterStatus status="STOPPED" label="Stopped" refresh={this.refreshExecutions}/>
              </div>
            </FilterSection>

          </div>
        </div>
      </div>
    );
  }
}

const FilterCheckbox = (props: { pipeline: string, visible: boolean, update: () => void }): JSX.Element => {
  const { pipeline, visible, update } = props;
  const sortFilter = ReactInjector.executionFilterModel.asFilterModel.sortFilter;
  const changeHandler = () => {
    ReactGA.event({ category: 'Pipelines', action: 'Filter: pipeline', label: pipeline });
    sortFilter.pipeline[pipeline] = !sortFilter.pipeline[pipeline];
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
}

const Pipeline = SortableElement((props: { pipeline: string, dragEnabled: boolean, update: () => void }) => (
  <div className="checkbox sortable">
  <div>
    <label>
      {props.dragEnabled && <DragHandle/>}
      <FilterCheckbox pipeline={props.pipeline} visible={!props.dragEnabled} update={props.update}/>
      {props.pipeline}
    </label>
  </div>
</div>
));

const Pipelines = SortableContainer((props: { names: string[], dragEnabled: boolean, update: () => void }) => (
  <div>
    {props.names.map((pipeline, index) => <Pipeline key={pipeline} index={index} pipeline={pipeline} disabled={!props.dragEnabled} dragEnabled={props.dragEnabled} update={props.update}/>)}
  </div>
));

const FilterStatus = (props: { status: string, label: string, refresh: () => void }): JSX.Element => {
  const sortFilter = ReactInjector.executionFilterModel.asFilterModel.sortFilter;
  const changed = () => {
    ReactGA.event({ category: 'Pipelines', action: 'Filter: status', label: props.label.toUpperCase() });
    sortFilter.status[props.status] = !sortFilter.status[props.status];
    props.refresh();
  }
  return (
    <div className="checkbox">
      <label>
        <input type="checkbox" checked={sortFilter.status[props.status] || false} onChange={changed}/>
        {props.label}
      </label>
    </div>
  );
}
