import { UISref, UISrefActive } from '@uirouter/react';
import React from 'react';

import { IInstanceCounts, LoadBalancerServerGroup } from '@spinnaker/core';

import { IEcsLoadBalancer, IEcsTargetGroup } from '../domain/IEcsLoadBalancer';

export interface IEcsTargetGroupProps {
  loadBalancer: IEcsLoadBalancer;
  targetGroup: IEcsTargetGroup;
  showServerGroups: boolean;
  showInstances: boolean;
}

export class EcsTargetGroup extends React.Component<IEcsTargetGroupProps> {
  public render(): React.ReactElement<EcsTargetGroup> {
    const { targetGroup, loadBalancer, showServerGroups } = this.props;

    const params = {
      loadBalancerName: loadBalancer.name,
      region: targetGroup.region,
      accountId: targetGroup.account,
      name: targetGroup.targetGroupName,
      vpcId: targetGroup.vpcId,
      provider: targetGroup.cloudProvider,
    };

    // info not yet available; HealthCounts component requires it,
    // but will not render if no values are defined.
    const emptyInstanceCounts: IInstanceCounts = {
      up: undefined,
      down: undefined,
      starting: undefined,
      succeeded: undefined,
      failed: undefined,
      unknown: undefined,
      outOfService: undefined,
    };

    return (
      <div className="target-group-container container-fluid no-padding">
        <UISrefActive class="active">
          <UISref to=".ecsTargetGroupDetails" params={params}>
            <div className={`clickable clickable-row row no-margin-y target-group-header`}>
              <div className="col-md-8 target-group-title">{targetGroup.targetGroupName}</div>
            </div>
          </UISref>
        </UISrefActive>
        {showServerGroups &&
          targetGroup.serverGroups.map((sgName) => {
            return (
              <LoadBalancerServerGroup
                key={sgName}
                account={targetGroup.account}
                region={targetGroup.region}
                serverGroup={{
                  account: targetGroup.account,
                  cloudProvider: targetGroup.cloudProvider,
                  cluster: sgName,
                  instanceCounts: emptyInstanceCounts,
                  instances: targetGroup.instances,
                  name: sgName,
                  type: targetGroup.type,
                  region: targetGroup.region,
                }}
                showInstances={false} // TODO: use prop when instanceCounts available
              />
            );
          })}
      </div>
    );
  }
}
