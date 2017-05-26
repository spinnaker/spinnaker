import * as React from 'react';
import { orderBy } from 'lodash';

import { ILoadBalancer, IServerGroup } from 'core/domain';

import { LoadBalancerInstances } from './LoadBalancerInstances';
import { LoadBalancerServerGroup } from './LoadBalancerServerGroup';

export interface ILoadBalancerClusterContainerProps {
  loadBalancer: ILoadBalancer;
  serverGroups: IServerGroup[];
  showServerGroups?: boolean;
  showInstances?: boolean;
}

export class LoadBalancerClusterContainer extends React.Component<ILoadBalancerClusterContainerProps, void> {
  public render(): React.ReactElement<LoadBalancerClusterContainer> {
    const { loadBalancer, serverGroups, showInstances, showServerGroups } = this.props;

    const ServerGroups = orderBy(serverGroups, ['isDisabled', 'name'], ['asc', 'desc']).map((serverGroup) => (
      <LoadBalancerServerGroup
        key={serverGroup.name}
        account={loadBalancer.account}
        region={loadBalancer.region}
        cloudProvider={loadBalancer.cloudProvider}
        serverGroup={serverGroup}
        showInstances={showInstances}
      />
    ));

    return (
      <div className="cluster-container">
        {showServerGroups && ServerGroups}
        {!showServerGroups && showInstances && <LoadBalancerInstances serverGroups={loadBalancer.serverGroups} instances={loadBalancer.instances} />}
      </div>
    );
  }
}
