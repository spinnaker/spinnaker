import React from 'react';

import type { IInstanceDetailsProps } from '@spinnaker/core';
import { InstanceDetailsPane } from '@spinnaker/core';

export function EcsInstanceDetails({ $stateParams }: IInstanceDetailsProps) {
  return (
    <InstanceDetailsPane>
      <h3>Instance Details</h3>
      <dl className="dl-horizontal dl-narrow">
        <dt>Instance ID</dt>
        <dd>{$stateParams.instanceId}</dd>
        <dt>Provider</dt>
        <dd>ecs</dd>
      </dl>
      <div className="alert alert-warning">
        ECS instance health, networking, and actions are not available in this React details view yet.
      </div>
    </InstanceDetailsPane>
  );
}
