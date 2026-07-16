import React from 'react';

import type { IServerGroupDetailsSectionProps } from '@spinnaker/core';
import { AccountTag, CollapsibleSection, timestamp } from '@spinnaker/core';

export function EcsServerGroupInformationSection({ serverGroup }: IServerGroupDetailsSectionProps) {
  return (
    <CollapsibleSection heading="Server Group Information" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Account</dt>
        <dd>
          <AccountTag account={serverGroup.account} />
        </dd>
        <dt>Region</dt>
        <dd>{serverGroup.region}</dd>
        <dt>Created</dt>
        <dd>{serverGroup.createdTime ? timestamp(serverGroup.createdTime) : 'Unknown'}</dd>
        <dt>ECS Cluster</dt>
        <dd>{(serverGroup as any).ecsCluster}</dd>
        <dt>VPC ID</dt>
        <dd>{(serverGroup as any).vpcId}</dd>
      </dl>
    </CollapsibleSection>
  );
}
