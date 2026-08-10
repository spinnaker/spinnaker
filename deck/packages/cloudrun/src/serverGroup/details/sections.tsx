import React from 'react';

import type { IServerGroupDetailsSectionProps } from '@spinnaker/core';
import { AccountTag, CollapsibleSection, HealthCounts, timestamp } from '@spinnaker/core';

export function CloudrunServerGroupInformationSection({ serverGroup }: IServerGroupDetailsSectionProps) {
  return (
    <CollapsibleSection heading="Server Group Information" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Created</dt>
        <dd>{timestamp(serverGroup.createdTime)}</dd>
        <dt>In</dt>
        <dd>
          <AccountTag account={serverGroup.account} />
        </dd>
        <dt>Region</dt>
        <dd>{serverGroup.region}</dd>
      </dl>
    </CollapsibleSection>
  );
}

export function CloudrunServerGroupSizeSection({ serverGroup }: IServerGroupDetailsSectionProps) {
  const capacity = serverGroup.capacity || ({} as any);
  const current = serverGroup.instances?.length || 0;
  return (
    <CollapsibleSection heading="Size" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        {capacity.min === capacity.max ? (
          <>
            <dt>Min/Max</dt>
            <dd>{capacity.min}</dd>
          </>
        ) : (
          <>
            <dt>Min</dt>
            <dd>{capacity.min}</dd>
            <dt>Max</dt>
            <dd>{capacity.max}</dd>
          </>
        )}
        <dt>Current</dt>
        <dd>{current}</dd>
      </dl>
    </CollapsibleSection>
  );
}

export function CloudrunServerGroupHealthSection({ serverGroup }: IServerGroupDetailsSectionProps) {
  return (
    <CollapsibleSection heading="Health" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Instances</dt>
        <dd>
          <HealthCounts container={serverGroup.instanceCounts} className="pull-left" />
        </dd>
      </dl>
    </CollapsibleSection>
  );
}
