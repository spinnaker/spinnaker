import { isEqual, orderBy } from 'lodash';
import React from 'react';

import { LoadBalancerInstances } from './LoadBalancerInstances';
import { LoadBalancerServerGroup } from './LoadBalancerServerGroup';
import { ILoadBalancer, IServerGroup } from '../domain';

export interface ILoadBalancerClusterContainerProps {
  loadBalancer: ILoadBalancer;
  serverGroups: IServerGroup[];
  showServerGroups?: boolean;
  showInstances?: boolean;
}

export class LoadBalancerClusterContainer extends React.Component<ILoadBalancerClusterContainerProps> {
  public shouldComponentUpdate(nextProps: ILoadBalancerClusterContainerProps) {
    const serverGroupsEqual = () =>
      isEqual(
        (nextProps.serverGroups || []).map((g) => g.name),
        (this.props.serverGroups || []).map((g) => g.name),
      );
    return (
      nextProps.showInstances !== this.props.showInstances ||
      nextProps.showServerGroups !== this.props.showServerGroups ||
      nextProps.loadBalancer !== this.props.loadBalancer ||
      !serverGroupsEqual()
    );
  }

  public render(): React.ReactElement<LoadBalancerClusterContainer> {
    const { loadBalancer, serverGroups, showInstances, showServerGroups } = this.props;

    const ServerGroups = orderBy(serverGroups, ['isDisabled', 'name'], ['asc', 'desc']).map((serverGroup) => (
      <LoadBalancerServerGroup
        key={serverGroup.name}
        account={serverGroup.account}
        region={serverGroup.region}
        serverGroup={serverGroup}
        showInstances={showInstances}
      />
    ));

    return (
      <div className="cluster-container">
        {showServerGroups && ServerGroups}
        {!showServerGroups && showInstances && (
          <LoadBalancerInstances serverGroups={loadBalancer.serverGroups} instances={loadBalancer.instances} />
        )}
      </div>
    );
  }
}
