import { UIRouterContext } from '@uirouter/react-hybrid';
import * as React from 'react';
import { AutoSizer, CellMeasurer, CellMeasurerCache, List, ListRowProps } from 'react-virtualized';
import { Subscription } from 'rxjs';

import { ReactInjector } from 'core/reactShims';
import { Application } from 'core/application';
import { ClusterPod } from 'core/cluster/ClusterPod';
import { Spinner } from 'core/widgets/spinners/Spinner';
import { ISortFilter } from 'core/filterModel';
import { ClusterState } from 'core/state';

import { IClusterGroup, IClusterSubgroup } from './filter/ClusterFilterService';

export interface IAllClustersGroupingsProps {
  app: Application;
  initialized: boolean;
}

export interface IAllClustersGroupingsState {
  groups: IClusterSubgroup[];
  sortFilter: ISortFilter;
  scrollToRow?: number;
}

@UIRouterContext
export class AllClustersGroupings extends React.Component<IAllClustersGroupingsProps, IAllClustersGroupingsState> {
  private clusterFilterService = ClusterState.filterService;
  private clusterFilterModel = ClusterState.filterModel;

  private groupsSubscription: Subscription;
  private unwatchSortFilter: Function;

  private cellCache: CellMeasurerCache;

  constructor(props: IAllClustersGroupingsProps) {
    super(props);
    this.cellCache = new CellMeasurerCache({
      fixedWidth: true,
      minHeight: 100,
      defaultHeight: 177,
      keyMapper: rowIndex => {
        // instances have a fixed width (unless the details are shown), so use that to optimize row height measurement
        const group = this.state.groups[rowIndex];
        const instanceCountKeys: string[] = [];
        group.subgroups.forEach(subGroup => {
          const subKeys: number[] = [];
          subGroup.serverGroups.forEach(serverGroup => {
            subKeys.push(serverGroup.instances.length);
          });
          instanceCountKeys.push(subKeys.sort().join(','));
        });
        const instanceCountKey = instanceCountKeys.sort().join('; ');
        if (this.clusterFilterModel.asFilterModel.sortFilter.listInstances) {
          // NOTE: this is not perfect! If the same cluster might have different row heights for its instances and a
          // users gets the filters set so those different instances show up (but in the exact same quantity), this
          // could return an incorrect calculation. Seems unlikely, however, if you're reading this because someone is
          // complaining about row heights getting weird, sorry.
          return `${group.key}: ${instanceCountKey}`;
        }
        return instanceCountKey;
      },
    });
    this.state = {
      groups: this.clusterFilterModel.asFilterModel.groups.reduce((a, b) => a.concat(b.subgroups), []),
      sortFilter: this.clusterFilterModel.asFilterModel.sortFilter,
    };
  }

  public componentDidMount() {
    const onGroupsChanged = (groups: IClusterGroup[]) => {
      this.setState({ groups: groups.reduce((a, b) => a.concat(b.subgroups), []) });
    };
    this.groupsSubscription = this.clusterFilterService.groupsUpdatedStream.subscribe(onGroupsChanged);

    const getSortFilter = () => this.clusterFilterModel.asFilterModel.sortFilter;
    const onFilterChanged = ({ ...sortFilter }: any) => {
      const shouldResetCache = sortFilter.listInstances !== this.state.sortFilter.listInstances;
      this.setState({ sortFilter }, () => shouldResetCache && this.cellCache.clearAll());
    };
    // TODO: Remove $rootScope. Keeping it here so we can use $watch for now.
    //       Eventually, there should be events fired when filters change.
    this.unwatchSortFilter = ReactInjector.$rootScope.$watch(getSortFilter, onFilterChanged, true);
    const { $stateParams } = ReactInjector;
    // Automatically scroll server group into view if deep linked
    if ($stateParams.serverGroup) {
      this.clusterFilterService.groupsUpdatedStream.take(1).subscribe(() => {
        const scrollToRow = this.state.groups.findIndex(group =>
          group.subgroups.some(subgroup =>
            subgroup.serverGroups.some(
              sg =>
                sg.account === $stateParams.accountId &&
                sg.name === $stateParams.serverGroup &&
                sg.region === $stateParams.region,
            ),
          ),
        );
        this.setState({ scrollToRow });
      });
    }
  }

  public componentWillUnmount() {
    this.groupsSubscription.unsubscribe();
    this.unwatchSortFilter();
  }

  private renderRow = (props: ListRowProps): JSX.Element => {
    const { app } = this.props;
    const { groups = [], sortFilter } = this.state;
    const group = groups[props.index];
    const parent: React.ReactType = props.parent as any; // bad cast, sad @types
    return (
      <CellMeasurer cache={this.cellCache} key={props.key} rowIndex={props.index} parent={parent}>
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
  };

  private noRowsRender = (): JSX.Element => {
    const dataSource = this.props.app.getDataSource('serverGroups');
    if (!dataSource.data.length && !dataSource.fetchOnDemand) {
      return <h4 className="text-center">No server groups found in this application</h4>;
    }
    if (this.props.app.getDataSource('serverGroups').fetchOnDemand) {
      const filtered = Object.keys(this.state.sortFilter.clusters).length;
      if (!filtered) {
        return null;
      }
    }
    return <h4 className="text-center">No server groups match the filters you've selected</h4>;
  };

  public render() {
    const { initialized } = this.props;
    const { groups = [] } = this.state;

    if (!initialized) {
      return <Spinner size="medium" />;
    }

    return (
      <AutoSizer>
        {({ width, height }) => (
          <List
            className={'rollup'}
            height={height}
            width={width}
            rowCount={groups.length}
            deferredMeasurementCache={this.cellCache}
            rowHeight={this.cellCache.rowHeight}
            rowRenderer={this.renderRow}
            noRowsRenderer={this.noRowsRender}
            scrollToAlignment="start"
            scrollToIndex={this.state.scrollToRow}
            overscanRowCount={3}
            containerStyle={{ overflow: 'visible' }}
          />
        )}
      </AutoSizer>
    );
  }
}
