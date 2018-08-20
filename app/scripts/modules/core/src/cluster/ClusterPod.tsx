import * as React from 'react';
import { orderBy } from 'lodash';

import { ClusterState } from 'core/state';
import { ServerGroup } from 'core/serverGroup/ServerGroup';
import { Application } from 'core/application';
import { EntityNotifications } from 'core/entityTag/notifications/EntityNotifications';
import { IServerGroup } from 'core/domain';
import { Tooltip } from 'core/presentation';
import { IClusterSubgroup, IServerGroupSubgroup } from './filter/ClusterFilterService';
import { ISortFilter } from 'core/filterModel';
import { ClusterPodTitleWrapper } from 'core/cluster/ClusterPodTitleWrapper';
import { ServerGroupManager } from 'core/serverGroupManager/ServerGroupManager';

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
    const hasMoniker = subgroup.serverGroups.every(sg => {
      return !!sg.moniker;
    });
    let iteratee;
    if (hasMoniker) {
      iteratee = 'moniker.sequence';
    } else {
      iteratee = 'name';
    }

    const sortedServerGroups = orderBy(subgroup.serverGroups, [iteratee], ['desc']);

    return (
      <div className="pod-subgroup" key={subgroup.key}>
        <h6 className="sticky-header-2 subgroup-title horizontal middle">
          <div>{subgroup.heading}</div>
          <EntityNotifications
            entity={subgroup}
            application={application}
            placement="top"
            hOffsetPercent="20%"
            entityType="cluster"
            pageLocation="pod"
            onUpdate={() => application.serverGroups.refresh()}
          />
        </h6>

        {(subgroup.serverGroupManagers || []).map(manager => (
          <ServerGroupManager
            key={manager.key}
            manager={manager}
            grouping={grouping}
            application={application}
            sortFilter={sortFilter}
          />
        ))}

        {grouping.cluster.category === 'serverGroup' &&
          sortedServerGroups.map((serverGroup: IServerGroup) => (
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
