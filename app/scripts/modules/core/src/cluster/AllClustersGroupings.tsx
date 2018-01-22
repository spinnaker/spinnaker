import { UIRouterContext } from '@uirouter/react-hybrid';
import { IClusterSubgroup } from 'core/cluster/filter/clusterFilter.service';
import { BindAll } from 'lodash-decorators';
import * as React from 'react';
import { Subscription } from 'rxjs/Subscription';

import { AutoSizer, CellMeasurer, CellMeasurerCache, List, ListRowProps } from 'react-virtualized';

import { ReactInjector } from 'core/reactShims';
import { Application } from 'core/application';
import { ClusterPod } from 'core/cluster/ClusterPod';
import { IClusterGroup } from './filter/clusterFilter.service';
import { Spinner } from 'core/widgets/spinners/Spinner'

export interface IAllClustersGroupingsProps {
  app: Application;
  initialized: boolean;
}

export interface IAllClustersGroupingsState {
  groups: IClusterSubgroup[];
  sortFilter: any;
}

@UIRouterContext
@BindAll()
export class AllClustersGroupings extends React.Component<IAllClustersGroupingsProps, IAllClustersGroupingsState> {
  private clusterFilterService = ReactInjector.clusterFilterService;
  private clusterFilterModel = ReactInjector.clusterFilterModel;

  private groupsSubscription: Subscription;
  private unwatchSortFilter: Function;

  private cellCache: CellMeasurerCache;

  constructor(props: IAllClustersGroupingsProps) {
    super(props);
    this.cellCache = new CellMeasurerCache({ fixedWidth: true, minHeight: 100, defaultHeight: 177 });
    this.state = {
      groups: this.clusterFilterModel.asFilterModel.groups.reduce((a, b) => a.concat(b.subgroups), []),
      sortFilter: this.clusterFilterModel.asFilterModel.sortFilter,
    };
  }

  public componentDidMount() {
    const onGroupsChanged = (groups: IClusterGroup[]) => {
      this.setState(
        { groups: groups.reduce((a, b) => a.concat(b.subgroups), []) },
        () => this.cellCache.clearAll()
      );
    };
    this.groupsSubscription = this.clusterFilterService.groupsUpdatedStream.subscribe(onGroupsChanged);

    const getSortFilter = () => this.clusterFilterModel.asFilterModel.sortFilter;
    const onFilterChanged = ({ ...sortFilter }: any) => {
      this.setState(
        { sortFilter },
        () => this.cellCache.clearAll()
      );
    };
    // TODO: Remove $rootScope. Keeping it here so we can use $watch for now.
    //       Eventually, there should be events fired when filters change.
    this.unwatchSortFilter = ReactInjector.$rootScope.$watch(getSortFilter, onFilterChanged, true);
  }

  public componentWillReceiveProps() {
    this.cellCache.clearAll();
  }

  public componentWillUnmount() {
    this.groupsSubscription.unsubscribe();
    this.unwatchSortFilter();
  }

  private renderRow(props: ListRowProps): JSX.Element {
    const { app } = this.props;
    const { groups = [], sortFilter } = this.state;
    const group = groups[props.index];
    const parent: React.ReactType = props.parent as any; // bad cast, sad @types
    return (
      <CellMeasurer
        cache={this.cellCache}
        key={props.key}
        rowIndex={props.index}
        parent={parent}
      >
        <div key={props.index} style={props.style}>
          <ClusterPod
            grouping={group}
            application={app}
            parentHeading={group.cluster.serverGroups[0].account}
            sortFilter={sortFilter}
          />
        </div>
      </CellMeasurer>
    );
  }

  private noRowsRender(): JSX.Element {
    return <h4 className="text-center">No server groups match the filters you've selected.</h4>;
  }

  public render() {
    const { initialized } = this.props;
    const { groups = [] } = this.state;

    if (!initialized) {
      return <Spinner size="medium" />;
    }

    return (
      <AutoSizer>
        {({ width, height }) =>
          <List
            className={'rollup'}
            height={height}
            width={width}
            rowCount={groups.length}
            // deferredMeasurementCache={this.cellCache}
            rowHeight={this.cellCache.rowHeight}
            rowRenderer={this.renderRow}
            noRowsRenderer={this.noRowsRender}
            overscanRowCount={3}
            containerStyle={{ overflow: 'visible' }}
          />}
      </AutoSizer>
    );
  }
}

