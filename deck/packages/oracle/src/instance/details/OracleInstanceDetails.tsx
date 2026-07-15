import React from 'react';
import { CollapsibleSection, timestamp } from '@spinnaker/core';

export function OracleInstanceDetails({ instance }: any) {
  return (
    <CollapsibleSection heading="Instance Information" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Launched</dt>
        <dd>{instance.launchTime ? timestamp(instance.launchTime) : '(Unknown)'}</dd>
        <dt>In</dt>
        <dd>{instance.availabilityZone}</dd>
        <dt>Type</dt>
        <dd>{instance.instanceType}</dd>
        <dt>Server Group</dt>
        <dd>{instance.serverGroup}</dd>
        <dt>Network</dt>
        <dd>{instance.network}</dd>
      </dl>
    </CollapsibleSection>
  );
}
