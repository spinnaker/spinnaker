import * as React from 'react';
import { get, groupBy } from 'lodash';
import { Subject } from 'rxjs/Subject';
import autoBindMethods from 'class-autobind-decorator';

import { Application } from 'core/application/application.model';
import { ApplicationDataSource } from 'core/application/service/applicationDataSource';
import { IExecution } from 'core/domain';
import { Execution } from 'core/delivery/executionGroup/execution/Execution';
import { Sticky } from 'core/utils/stickyHeader/Sticky';
import { collapsibleSectionStateCache } from 'core/cache/collapsibleSectionStateCache';
import { $state } from 'core/uirouter';
import { IFilterTag } from 'core/filterModel/FilterTags';

import './FastPropertyRollouts.less';

interface IProps {
  application: Application,
  filters: IFilterTag[],
  filtersUpdatedStream: Subject<IFilterTag[]>,
}

interface IState {
  open: boolean;
  filteredExecutions: IExecution[];
  loading: boolean;
  loadError: boolean;
}

@autoBindMethods
export class FastPropertyRollouts extends React.Component<IProps, IState> {
  private dataSourceUnsubscribe: () => any;
  private dataSource: ApplicationDataSource;
  private runningDataSource: ApplicationDataSource;
  private isGlobal: boolean;

  constructor(props: IProps) {
    super(props);
    const sectionCacheKey = this.getSectionCacheKey();
    this.isGlobal = this.props.application.global;
    this.state = {
      open: this.isGlobal || !collapsibleSectionStateCache.isSet(sectionCacheKey) || collapsibleSectionStateCache.isExpanded(sectionCacheKey),
      filteredExecutions: [],
      loading: true,
      loadError: false,
    };
    this.dataSource = props.application.getDataSource('propertyPromotions');
    this.runningDataSource = props.application.getDataSource('runningPropertyPromotions');
    this.props.filtersUpdatedStream.subscribe(() => this.filterExecutions());
  }

  public componentDidMount(): void {
    this.dataSource.activate();
    this.dataSourceUnsubscribe = this.dataSource.onRefresh(null, () => this.filterExecutions());
    this.dataSource.ready().then(
      () => this.setState({loading: false, loadError: false}),
      () => this.setState({loading: false, loadError: true})
    );
    if (!this.state.open && $state.current.name.endsWith('.execution')) {
      this.setState({open: true});
    }
  }

  public componentWillUnmount(): void {
    this.dataSource.deactivate();
    if (this.dataSourceUnsubscribe) {
      this.dataSourceUnsubscribe();
    }
  }

  public componentWillReceiveProps(): void {
    this.filterExecutions();
  }

  private toggle(): void {
    const open = !this.state.open;
    this.setState({open});
    if (!open && $state.current.name.endsWith('.execution')) {
      $state.go('^');
    }
    collapsibleSectionStateCache.setExpanded(this.getSectionCacheKey(), open);
  }

  private getSectionCacheKey(): string {
    const appName = this.props.application ? this.props.application.name : '#global';
    return [appName, 'fastProperty', 'promotions'].join('#');
  }

  private filterExecutions(): void {
    const { filters } = this.props;
    const groupedFilters = groupBy(filters, 'label');
    const filteredExecutions = (this.dataSource.data || []).filter(execution => {
      const affectedProperties: any[] = (execution.context.persistedProperties || []).concat(execution.context.originalProperties || []);
      return affectedProperties.some(p => {
        return (!filters || Object.keys(groupedFilters).every(k => {
          if (k === 'substring') {
            return groupedFilters[k].some(f => JSON.stringify(execution).includes(f.value));
          }
          return groupedFilters[k].some(f => p[k] === f.value);
        }));
      });
    });
    this.setState({filteredExecutions});
  }

  public render() {
    const runningCount = this.runningDataSource.data.length;
    const executionCount = this.dataSource.data.length;
    const isFiltered = this.state.filteredExecutions.length < executionCount;
    const executions = this.state.filteredExecutions.map((execution: IExecution) => {
      const propertyAction = get<string>(execution.context, 'propertyAction', 'unknown').toLowerCase();
      const propertyKey = get<string>(execution.context, 'persistedProperties[0].key') ||
        get<string>(execution.context, 'originalProperties[0].property.key') || '[unknown property]';
      const title = `${propertyKey} (${propertyAction})`;
      return (
        <Execution
          key={execution.id}
          execution={execution}
          application={this.props.application}
          dataSourceKey="propertyPromotions"
          title={title}
        />
      );
    }
    );
    return (
      <div className="fast-property-promotions show-durations">
        {!this.isGlobal && (
          <Sticky className="clickable rollup-title sticky-header" onClick={this.toggle} topOffset={-3}>
            <span className={`small glyphicon toggle glyphicon-chevron-${this.state.open ? 'down' : 'right'}`}/>
            <h4 className="shadowed">
              Rollouts (
              {isFiltered && (<span>{this.state.filteredExecutions.length} of {executionCount}</span>)}
              {!isFiltered && <span>{executionCount}</span>}
              )
              {runningCount > 0 && (<span className="badge badge-running-count">{runningCount}</span>)}
            </h4>
          </Sticky>
        )}
        {this.state.open && (
          <div className="executions">
            {!this.state.loading && executions}
            {this.state.loading && <h3 className="text-center"><span className="fa fa-cog fa-spin"/></h3>}
            {this.state.loadError && <div className="text-center">There was an error loading rollouts. We'll try again shortly.</div>}
            {!this.state.loading && !executions.length && <div className="text-center">No rollouts found</div>}
          </div>
        )}
      </div>
    );
  }
}
