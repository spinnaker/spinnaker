import { UISref, UISrefActive } from '@uirouter/react';
import { orderBy } from 'lodash';
import React from 'react';

import { HealthCounts, LoadBalancerInstances, LoadBalancerServerGroup } from '@spinnaker/core';
import { IAmazonApplicationLoadBalancer, ITargetGroup } from '../domain/IAmazonLoadBalancer';

import './targetGroup.less';

export interface ITargetGroupProps {
  loadBalancer: IAmazonApplicationLoadBalancer;
  targetGroup: ITargetGroup;
  showServerGroups: boolean;
  showInstances: boolean;
}

export class TargetGroup extends React.Component<ITargetGroupProps> {
  public render(): React.ReactElement<TargetGroup> {
    const { targetGroup, showInstances, showServerGroups, loadBalancer } = this.props;

    const ServerGroups = orderBy(
      targetGroup.serverGroups,
      ['isDisabled', 'name'],
      ['asc', 'desc'],
    ).map((serverGroup) => (
      <LoadBalancerServerGroup
        key={serverGroup.name}
        account={serverGroup.account}
        region={serverGroup.region}
        serverGroup={serverGroup}
        showInstances={showInstances}
      />
    ));

    const params = {
      loadBalancerName: loadBalancer.name,
      region: targetGroup.region,
      accountId: targetGroup.account,
      name: targetGroup.name,
      vpcId: targetGroup.vpcId,
      provider: targetGroup.cloudProvider,
    };

    return (
      <div className="target-group-container container-fluid no-padding">
        <UISrefActive class="active">
          <UISref to=".targetGroupDetails" params={params}>
            <div className={`clickable clickable-row row no-margin-y target-group-header`}>
              <div className="col-md-8 target-group-title">{targetGroup.name}</div>
              <div className="col-md-4 text-right">
                <HealthCounts container={targetGroup.instanceCounts} />
              </div>
            </div>
          </UISref>
        </UISrefActive>
        {showServerGroups && ServerGroups}
        {!showServerGroups && showInstances && (
          <LoadBalancerInstances serverGroups={targetGroup.serverGroups} instances={targetGroup.instances} />
        )}
      </div>
    );
  }
}
