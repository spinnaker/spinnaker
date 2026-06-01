import React from 'react';

import type { Application } from '@spinnaker/core';
import { AccountTag, CloudProviderLogo } from '@spinnaker/core';

interface IEcsSecurityGroupDetailsProps {
  app: Application;
  resolvedSecurityGroup: {
    accountId: string;
    name: string;
    region: string;
  };
}

export function EcsSecurityGroupDetails({ resolvedSecurityGroup }: IEcsSecurityGroupDetailsProps) {
  return (
    <div className="details-panel">
      <div className="header">
        <div className="header-text horizontal middle">
          <CloudProviderLogo provider="ecs" height="36px" width="36px" />
          <h3>{resolvedSecurityGroup.name}</h3>
        </div>
      </div>
      <div className="content">
        <dl className="dl-horizontal dl-narrow">
          <dt>Account</dt>
          <dd>
            <AccountTag account={resolvedSecurityGroup.accountId} />
          </dd>
          <dt>Region</dt>
          <dd>{resolvedSecurityGroup.region}</dd>
        </dl>
        <div className="alert alert-warning">
          ECS security group rules and VPC details are not available in this React details view yet.
        </div>
      </div>
    </div>
  );
}
