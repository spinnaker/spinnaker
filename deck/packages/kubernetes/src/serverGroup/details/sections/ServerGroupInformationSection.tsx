import { UISref } from '@uirouter/react';

import React from 'react';
import { AccountTag, CollapsibleSection, robotToHuman, timestamp } from '@spinnaker/core';
import type { IKubernetesServerGroupDetailsSectionProps } from './IKubernetesServerGroupDetailsSectionProps';
import { useManifest } from '../useManifest';

export function ServerGroupInformationSection({ serverGroup }: IKubernetesServerGroupDetailsSectionProps) {
  const { manifest } = serverGroup;
  const { manifestController } = useManifest({ manifest });

  return (
    <CollapsibleSection heading="Information" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Created</dt>
        <dd>{timestamp(serverGroup.createdTime)}</dd>
        <dt>Account</dt>
        <dd>
          <AccountTag account={serverGroup.account} />
        </dd>
        <dt>Namespace</dt>
        <dd>{serverGroup.namespace}</dd>
        <dt>Kind</dt>
        <dd>{serverGroup.kind}</dd>
        {manifestController && (
          <>
            <dt>Controller</dt>
            <UISref
              to="^.serverGroupManager"
              params={{
                accountId: serverGroup.account,
                region: serverGroup.region,
                serverGroupManager: manifestController,
                provider: 'kubernetes',
              }}
            >
              <a>
                <dd>{robotToHuman(manifestController)}</dd>
              </a>
            </UISref>
          </>
        )}
      </dl>
    </CollapsibleSection>
  );
}
