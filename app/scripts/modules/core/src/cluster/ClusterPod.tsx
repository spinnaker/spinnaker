import classNames from 'classnames';
import { groupBy, map, orderBy, partition } from 'lodash';
import React from 'react';

import { ClusterPodTitleWrapper } from './ClusterPodTitleWrapper';
import { Application } from '../application';
import { IServerGroup } from '../domain';
import { EntityNotifications } from '../entityTag/notifications/EntityNotifications';
import { IClusterSubgroup, IServerGroupSubgroup } from './filter/ClusterFilterService';
import { ISortFilter } from '../filterModel';
import { ManagedResourceStatusIndicator } from '../managed';
import { Tooltip } from '../presentation';
import { ServerGroup } from '../serverGroup/ServerGroup';
import { ServerGroupManager } from '../serverGroupManager/ServerGroupManager';
import { ClusterState } from '../state';

export interface IClusterPodProps {
  grouping: IClusterSubgroup;
  application: Application;
  parentHeading: string;
  sortFilter: ISortFilter;
}

export interface IClusterPodState {
  showCloseButton: boolean;
}

export class ClusterPod extends React.Component<IClusterPodProps, IClusterPodState> {
  constructor(props: IClusterPodProps) {
    super(props);

    this.state = {
      showCloseButton: props.application.getDataSource('serverGroups').fetchOnDemand,
    };
  }

  public close = (): void => {
    const { parentHeading, grouping, application } = this.props;

    delete ClusterState.filterModel.asFilterModel.sortFilter.clusters[`${parentHeading}:${grouping.heading}`];
    ClusterState.filterModel.asFilterModel.applyParamsToUrl();
    application.getDataSource('serverGroups').refresh();
  };

  public render() {
    const { grouping } = this.props;
    const { showCloseButton } = this.state;

    return (
      <div className="row rollup-entry sub-group">
        <div className="sticky-header">
          <div className="rollup-summary">
            <ClusterPodTitleWrapper {...this.props} />
            {showCloseButton && (
              <div className="remove-button">
                <Tooltip value="Remove cluster from view">
                  <button className="btn btn-link" onClick={this.close}>
                    <span className="glyphicon glyphicon-remove" />
                  </button>
                </Tooltip>
              </div>
            )}
          </div>
        </div>

        <div className="rollup-details">{grouping.subgroups.map(this.renderSubGroup)}</div>
      </div>
    );
  }

  private renderSubGroup = (subgroup: IServerGroupSubgroup) => {
    const { grouping, application, sortFilter } = this.props;
    const hasMoniker = subgroup.serverGroups.every((sg) => {
      return !!sg.moniker;
    });
    let iteratee;
    if (hasMoniker) {
      iteratee = 'moniker.sequence';
    } else {
      iteratee = 'name';
    }

    const sortedServerGroups = orderBy(subgroup.serverGroups, [iteratee], ['desc']);
    // TODO(dpeach): similar grouping logic (e.g., by region, cluster, etc.)
    // happens in the ClusterFilterService. However, that service makes a lot of assumptions
    // about how the data is organized when diffing server groups after resource load or attaching
    // entity tags, running tasks, and running pipeline executions to server groups. Putting
    // this logic here seems fine while the server group manager grouping is still experimental.
    const [managedServerGroups, standaloneServerGroups] = partition(
      sortedServerGroups,
      (group) => group.serverGroupManagers && group.serverGroupManagers.length,
    );
    const serverGroupManagers = groupBy(managedServerGroups, (serverGroup) => serverGroup.serverGroupManagers[0].name);
    const showManagedIndicator = !grouping.isManaged && subgroup.isManaged;
    return (
      <div className="pod-subgroup" key={subgroup.key}>
        <h6 className="sticky-header-2 subgroup-title horizontal middle">
          <div>{subgroup.heading}</div>
          <div className={classNames('flex-container-h middle', { 'sp-margin-xs-left': showManagedIndicator })}>
            {showManagedIndicator && (
              <ManagedResourceStatusIndicator
                shape="circle"
                resourceSummary={subgroup.managedResourceSummary}
                application={application}
              />
            )}
            <EntityNotifications
              entity={subgroup}
              application={application}
              placement="top"
              hOffsetPercent="20%"
              entityType="cluster"
              pageLocation="pod"
              onUpdate={() => application.serverGroups.refresh()}
            />
          </div>
        </h6>

        {map(serverGroupManagers, (serverGroups, manager) => (
          <ServerGroupManager
            key={manager}
            manager={manager}
            grouping={grouping}
            serverGroups={serverGroups}
            application={application}
            sortFilter={sortFilter}
          />
        ))}

        {grouping.cluster.category === 'serverGroup' &&
          standaloneServerGroups.map((serverGroup: IServerGroup) => (
            <ServerGroup
              key={serverGroup.name}
              serverGroup={serverGroup}
              cluster={serverGroup.cluster}
              application={application}
              sortFilter={sortFilter}
              hasDiscovery={grouping.hasDiscovery}
              hasLoadBalancers={grouping.hasLoadBalancers}
            />
          ))}
      </div>
    );
  };
}
