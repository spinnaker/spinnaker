import { isEqual } from 'lodash';
import React from 'react';

import { ILoadBalancerClusterContainerProps } from '@spinnaker/core';

import { EcsTargetGroup } from './TargetGroup';
import { IEcsLoadBalancer } from '../domain/IEcsLoadBalancer';

export class EcsLoadBalancerClusterContainer extends React.Component<ILoadBalancerClusterContainerProps> {
  public shouldComponentUpdate(nextProps: ILoadBalancerClusterContainerProps) {
    const serverGroupsDiffer = () =>
      !isEqual(
        (nextProps.serverGroups || []).map((g) => g.name),
        (this.props.serverGroups || []).map((g) => g.name),
      );
    const targetGroupsDiffer = () =>
      !isEqual(
        ((nextProps.loadBalancer as IEcsLoadBalancer).targetGroups || []).map((t) => t.targetGroupName),
        ((this.props.loadBalancer as IEcsLoadBalancer).targetGroups || []).map((t) => t.targetGroupName),
      );
    return (
      nextProps.showInstances !== this.props.showInstances ||
      nextProps.showServerGroups !== this.props.showServerGroups ||
      nextProps.loadBalancer !== this.props.loadBalancer ||
      serverGroupsDiffer() ||
      targetGroupsDiffer()
    );
  }

  public render(): React.ReactElement<EcsLoadBalancerClusterContainer> {
    const { loadBalancer, showInstances, showServerGroups } = this.props;
    const lb = loadBalancer as IEcsLoadBalancer;

    const TargetGroups = lb.targetGroups.map((targetGroup) => {
      return (
        <EcsTargetGroup
          key={targetGroup.targetGroupName}
          loadBalancer={loadBalancer as IEcsLoadBalancer}
          targetGroup={targetGroup}
          showInstances={showInstances}
          showServerGroups={showServerGroups}
        />
      );
    });

    return <div className="cluster-container">{TargetGroups}</div>;
  }
}
