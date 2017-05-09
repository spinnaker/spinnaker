import * as React from 'react';
import { get, groupBy } from 'lodash';
import { Subject } from 'rxjs/Subject';
import autoBindMethods from 'class-autobind-decorator';

import { Application } from 'core/application/application.model';
import { ApplicationDataSource } from 'core/application/service/applicationDataSource';
import { IExecution } from 'core/domain';
import { Execution } from 'core/delivery/executionGroup/execution/Execution';
import { IFilterTag } from 'core/filterModel/FilterTags';

import './FastPropertyRollouts.less';

interface IProps {
  application: Application,
  filters: IFilterTag[],
  filtersUpdatedStream: Subject<IFilterTag[]>,
}

interface IState {
  filteredExecutions: IExecution[];
  loading: boolean;
  loadError: boolean;
}

@autoBindMethods
export class FastPropertyRollouts extends React.Component<IProps, IState> {
  private dataSourceUnsubscribe: () => any;
  private dataSource: ApplicationDataSource;
  private runningDataSource: ApplicationDataSource;

  constructor(props: IProps) {
    super(props);
    this.state = {
      filteredExecutions: [],
      loading: true,
      loadError: false,
    };
    this.dataSource = props.application.getDataSource('propertyPromotions');
    this.runningDataSource = props.application.getDataSource('runningPropertyPromotions');
  }

  public componentDidMount(): void {
    this.props.filtersUpdatedStream.subscribe(() => this.filterExecutions());
    this.dataSource.activate();
    this.dataSourceUnsubscribe = this.dataSource.onRefresh(null, () => this.filterExecutions());
    this.dataSource.ready().then(
      () => {
        this.filterExecutions();
        this.setState({loading: false, loadError: false});
      },
      () => this.setState({loading: false, loadError: true})
    );
  }

  public componentWillUnmount(): void {
    this.dataSource.deactivate();
    this.dataSourceUnsubscribe();
  }

  public componentWillReceiveProps(): void {
    this.filterExecutions();
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
        <div className="executions">
          {!this.state.loading && executions}
          {this.state.loading && <h3 className="text-center"><span className="fa fa-cog fa-spin"/></h3>}
          {this.state.loadError && <div className="text-center">There was an error loading rollouts. We'll try again shortly.</div>}
          {!this.state.loading && !executions.length && <div className="text-center">No rollouts found</div>}
        </div>
      </div>
    );
  }
}
