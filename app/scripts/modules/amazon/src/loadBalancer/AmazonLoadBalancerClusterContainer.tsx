import * as React from 'react';
import { BindAll } from 'lodash-decorators';

import { ILoadBalancerClusterContainerProps, LoadBalancerClusterContainer } from '@spinnaker/core'

import { IAmazonApplicationLoadBalancer } from '../domain/IAmazonLoadBalancer';
import { TargetGroup } from './TargetGroup';

@BindAll()
export class AmazonLoadBalancerClusterContainer extends React.Component<ILoadBalancerClusterContainerProps> {
  public render(): React.ReactElement<AmazonLoadBalancerClusterContainer> {
    const { loadBalancer, showInstances, showServerGroups } = this.props;

    if (loadBalancer.loadBalancerType !== 'classic') {
      const alb = loadBalancer as IAmazonApplicationLoadBalancer
      const TargetGroups = alb.targetGroups.map(targetGroup => {
        return (
          <TargetGroup
            key={targetGroup.name}
            loadBalancer={loadBalancer as IAmazonApplicationLoadBalancer}
            targetGroup={targetGroup}
            showInstances={showInstances}
            showServerGroups={showServerGroups}
          />
        );
      })
      return (
        <div className="cluster-container">
          {TargetGroups}
        </div>
      );
    } else {
      // Classic load balancer
      return <LoadBalancerClusterContainer {...this.props} />
    }
  }
}
