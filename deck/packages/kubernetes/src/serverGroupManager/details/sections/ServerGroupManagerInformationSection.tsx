import { UISref } from '@uirouter/react';
import { orderBy } from 'lodash';
import React from 'react';

import type { IServerGroupSummary } from '@spinnaker/core';
import { AccountTag, CollapsibleSection, robotToHuman, timestamp } from '@spinnaker/core';

import type { IKubernetesServerGroupManagerDetailsSectionProps } from './IKubernetesServerGroupManagerDetailsSectionProps';

export function ServerGroupManagerInformationSection({
  serverGroupManager,
}: IKubernetesServerGroupManagerDetailsSectionProps) {
  return (
    <CollapsibleSection heading="Information" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Created</dt>
        <dd>{timestamp(serverGroupManager.createdTime)}</dd>
        <dt>Account</dt>
        <dd>
          <AccountTag account={serverGroupManager.account} />
        </dd>
        <dt>Namespace</dt>
        <dd>{serverGroupManager.namespace}</dd>
        <dt>Kind</dt>
        <dd>{serverGroupManager.kind}</dd>
        {serverGroupManager.serverGroups.length > 0 && (
          <>
            <dt>Managing</dt>
            <dd>
              {orderBy(serverGroupManager.serverGroups, ['name'], ['asc']).map((serverGroup: IServerGroupSummary) => (
                <ul key={serverGroup.name}>
                  <UISref
                    to="^.serverGroup"
                    params={{
                      region: serverGroup.region,
                      accountId: serverGroup.account,
                      serverGroup: serverGroup.name,
                      provider: 'kubernetes',
                    }}
                  >
                    <a>{robotToHuman(serverGroup.name)}</a>
                  </UISref>
                </ul>
              ))}
            </dd>
          </>
        )}
      </dl>
    </CollapsibleSection>
  );
}
