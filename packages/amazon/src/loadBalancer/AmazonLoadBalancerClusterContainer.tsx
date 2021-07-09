import { isEqual } from 'lodash';
import React from 'react';

import { ILoadBalancerClusterContainerProps, LoadBalancerClusterContainer } from '@spinnaker/core';

import { TargetGroup } from './TargetGroup';
import { IAmazonApplicationLoadBalancer } from '../domain/IAmazonLoadBalancer';

export class AmazonLoadBalancerClusterContainer extends React.Component<ILoadBalancerClusterContainerProps> {
  public shouldComponentUpdate(nextProps: ILoadBalancerClusterContainerProps) {
    const serverGroupsDiffer = () =>
      !isEqual(
        (nextProps.serverGroups || []).map((g) => g.name),
        (this.props.serverGroups || []).map((g) => g.name),
      );
    const targetGroupsDiffer = () =>
      !isEqual(
        ((nextProps.loadBalancer as IAmazonApplicationLoadBalancer).targetGroups || []).map((t) => t.name),
        ((this.props.loadBalancer as IAmazonApplicationLoadBalancer).targetGroups || []).map((t) => t.name),
      );
    return (
      nextProps.showInstances !== this.props.showInstances ||
      nextProps.showServerGroups !== this.props.showServerGroups ||
      nextProps.loadBalancer !== this.props.loadBalancer ||
      serverGroupsDiffer() ||
      targetGroupsDiffer()
    );
  }

  public render(): React.ReactElement<AmazonLoadBalancerClusterContainer> {
    const { loadBalancer, showInstances, showServerGroups } = this.props;

    if (loadBalancer.loadBalancerType !== 'classic') {
      const alb = loadBalancer as IAmazonApplicationLoadBalancer;
      const TargetGroups = alb.targetGroups.map((targetGroup) => {
        return (
          <TargetGroup
            key={targetGroup.name}
            loadBalancer={loadBalancer as IAmazonApplicationLoadBalancer}
            targetGroup={targetGroup}
            showInstances={showInstances}
            showServerGroups={showServerGroups}
          />
        );
      });
      return <div className="cluster-container">{TargetGroups}</div>;
    } else {
      // Classic load balancer
      return <LoadBalancerClusterContainer {...this.props} />;
    }
  }
}
