import React from 'react';
import { CollapsibleSection } from '@spinnaker/core';

export function OracleSecurityGroupDetails({ resolvedSecurityGroup }: any) {
  return (
    <CollapsibleSection heading="Security Group Details" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Name</dt>
        <dd>{resolvedSecurityGroup.name}</dd>
        <dt>Account</dt>
        <dd>{resolvedSecurityGroup.accountId}</dd>
        <dt>Region</dt>
        <dd>{resolvedSecurityGroup.region}</dd>
        <dt>VCN</dt>
        <dd>{resolvedSecurityGroup.vpcId}</dd>
      </dl>
    </CollapsibleSection>
  );
}
