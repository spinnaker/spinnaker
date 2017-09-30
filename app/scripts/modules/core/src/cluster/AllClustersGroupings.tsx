import * as React from 'react';
import { Subscription } from 'rxjs/Subscription';

import { NgReact, ReactInjector } from 'core/reactShims';
import { Application } from 'core/application';
import { ClusterPod } from 'core/cluster/ClusterPod';
import { IClusterGroup } from './filter/clusterFilter.service';

export interface IAllClustersGroupingsProps {
  app: Application;
  initialized: boolean;
}

export interface IAllClustersGroupingsState {
  groups: IClusterGroup[];
  sortFilter: any;
}

export class AllClustersGroupings extends React.Component<IAllClustersGroupingsProps, IAllClustersGroupingsState> {
  private clusterFilterService = ReactInjector.clusterFilterService;
  private clusterFilterModel = ReactInjector.clusterFilterModel;

  private groupsSubscription: Subscription;
  private unwatchSortFilter: Function;

  constructor(props: IAllClustersGroupingsProps) {
    super(props);
    this.state = {
      groups: this.clusterFilterModel.asFilterModel.groups,
      sortFilter: this.clusterFilterModel.asFilterModel.sortFilter,
    };
  }

  public componentDidMount() {
    const onGroupsChanged = (groups: IClusterGroup[]) => this.setState({ groups });
    this.groupsSubscription = this.clusterFilterService.groupsUpdatedStream.subscribe(onGroupsChanged);

    const getSortFilter = () => this.clusterFilterModel.asFilterModel.sortFilter;
    const onFilterChanged = ({ ...sortFilter }: any) => this.setState({ sortFilter });
    // TODO: Remove $rootScope. Keeping it here so we can use $watch for now.
    //       Eventually, there should be events fired when filters change.
    this.unwatchSortFilter = ReactInjector.$rootScope.$watch(getSortFilter, onFilterChanged, true);
  }

  public componentWillUnmount() {
    this.groupsSubscription.unsubscribe();
    this.unwatchSortFilter();
  }

  public render() {
    const { app, initialized } = this.props;
    const { groups = [], sortFilter } = this.state;

    if (!initialized) {
      return <NgReact.LegacySpinner radius={30} width={8} length={16} />;
    }

    return (
      <div>
        {groups.map(group => {
          return (
            <div className="rollup" key={group.key}>
              {group.subgroups.map((subgroup: any, index: number) => (
                <ClusterPod
                  key={index}
                  grouping={subgroup}
                  application={app}
                  parentHeading={group.heading}
                  sortFilter={sortFilter}
                />
              ))}
            </div>
          );
        })}

        {groups.length === 0 && !app.serverGroups.fetchOnDemand && (
          <h4 className="text-center">No server groups match the filters you've selected.</h4>
        )}
      </div>
    );
  }
}

