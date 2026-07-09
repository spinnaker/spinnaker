import React from 'react';

import type { IServerGroupActionsProps, IServerGroupDetailsSectionProps } from '@spinnaker/core';
import { AccountTag, CollapsibleSection, HealthCounts, timestamp } from '@spinnaker/core';

export function ProxmoxServerGroupActions(_props: IServerGroupActionsProps): JSX.Element {
  return null;
}

export function ProxmoxServerGroupInformationSection({ serverGroup }: IServerGroupDetailsSectionProps): JSX.Element {
  const sg = serverGroup as any;
  return (
    <>
      <CollapsibleSection heading="Server Group Information" defaultExpanded={true}>
        <dl className="dl-horizontal dl-narrow">
          <dt>Created</dt>
          <dd>{timestamp(serverGroup.createdTime)}</dd>
          <dt>Account</dt>
          <dd>
            <AccountTag account={sg.account} />
          </dd>
          <dt>Node</dt>
          <dd>{serverGroup.region}</dd>
          {sg.cluster && (
            <>
              <dt>Cluster</dt>
              <dd>{sg.cluster}</dd>
            </>
          )}
        </dl>
      </CollapsibleSection>
      <CollapsibleSection heading="Size" defaultExpanded={true}>
        <dl className="dl-horizontal dl-narrow">
          <dt>Instances</dt>
          <dd>{serverGroup.instances?.length ?? 0}</dd>
        </dl>
      </CollapsibleSection>
      <CollapsibleSection heading="Health" defaultExpanded={true}>
        <dl className="dl-horizontal dl-narrow">
          <dt>Instances</dt>
          <dd>
            <HealthCounts container={serverGroup.instanceCounts} />
          </dd>
        </dl>
      </CollapsibleSection>
    </>
  );
}
