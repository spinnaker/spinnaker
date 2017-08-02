import * as React from 'react';
import autoBindMethods from 'class-autobind-decorator';
import { orderBy } from 'lodash';

import { NgReact, ReactInjector } from 'core/reactShims';
import { ServerGroup } from 'core/serverGroup/ServerGroup';
import { Application } from 'core/application';
import { EntityNotifications } from 'core/entityTag/notifications/EntityNotifications';
import { HealthCounts } from 'core/healthCounts';
import { IServerGroup } from 'core/domain';
import { Tooltip } from 'core/presentation';
import { IClusterSubgroup, IServerGroupSubgroup } from './filter/clusterFilter.service';

export interface IClusterPodProps {
  grouping: IClusterSubgroup;
  application: Application;
  parentHeading: string;
  sortFilter: any;
}

export interface IClusterPodState {
  showCloseButton: boolean;
}

@autoBindMethods
export class ClusterPod extends React.Component<IClusterPodProps, IClusterPodState> {
  constructor(props: IClusterPodProps) {
    super(props);

    this.state = {
      showCloseButton: props.application.getDataSource('serverGroups').fetchOnDemand,
    };
  }

  public close(): void {
    const { clusterFilterModel } = ReactInjector;
    const { parentHeading, grouping, application } = this.props;

    delete clusterFilterModel.asFilterModel.sortFilter.clusters[`${parentHeading}:${grouping.heading}`];
    clusterFilterModel.asFilterModel.applyParamsToUrl();
    application.getDataSource('serverGroups').refresh();
  }

  public render() {
    const { grouping } = this.props;

    return (
      <div className="row rollup-entry sub-group">
        <div className="sticky-header">
          <div className="rollup-summary">
            {this.renderPodTitle()}
          </div>
        </div>

        <div className="rollup-details">
          {grouping.subgroups.map(this.renderSubGroup)}
        </div>
      </div>
    );
  }

  private renderPodTitle() {
    const { AccountTag } = NgReact;
    const { grouping, application, parentHeading } = this.props;
    const { showCloseButton } = this.state;

    return (
      <div className="rollup-title-cell">
        <div className="heading-tag">
          <AccountTag account={parentHeading} />
        </div>

        <div className="pod-center">
          <div>
            <span className="glyphicon glyphicon-th"/>
            {' ' + grouping.heading}
          </div>

          <EntityNotifications
            entity={grouping}
            application={application}
            placement="top"
            hOffsetPercent="90%"
            entityType="cluster"
            pageLocation="pod"
            className="inverse"
            onUpdate={application.serverGroups.refresh}
          />
        </div>

        <HealthCounts container={grouping.cluster.instanceCounts}/>

        {showCloseButton && (
          <div className="remove-button">
            <Tooltip value="Remove cluster from view">
              <button className="btn btn-link" onClick={this.close}>
                <span className="glyphicon glyphicon-remove"/>
              </button>
            </Tooltip>
          </div>
        )}
      </div>
    )
  }

  private renderSubGroup(subgroup: IServerGroupSubgroup) {
    const { grouping, application, sortFilter } = this.props;
    const sortedServerGroups = orderBy(subgroup.serverGroups, ['name'], ['desc']);

    return (
      <div className="pod-subgroup" key={subgroup.key}>
        <h6 className="sticky-header-2 subgroup-title">
          {subgroup.heading}

          <EntityNotifications
            entity={subgroup}
            application={application}
            placement="top"
            hOffsetPercent="20%"
            entityType="cluster"
            pageLocation="pod"
            onUpdate={application.serverGroups.refresh}
          />
        </h6>

        {grouping.cluster.category === 'serverGroup' && sortedServerGroups.map((serverGroup: IServerGroup) => (
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
    )
  }
}
