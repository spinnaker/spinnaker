import React from 'react';

import { CollapsibleSection, HealthCounts } from '@spinnaker/core';
import type { IKubernetesServerGroupDetailsSectionProps } from './IKubernetesServerGroupDetailsSectionProps';

export function ServerGroupHealthSection({ serverGroup }: IKubernetesServerGroupDetailsSectionProps) {
  return (
    <CollapsibleSection heading="Health" defaultExpanded={true}>
      {serverGroup && (
        <dl className="dl-horizontal dl-narrow">
          <dt>Instances</dt>
          <dd>
            <HealthCounts container={serverGroup.instanceCounts} className="pull-left" />
          </dd>
        </dl>
      )}
    </CollapsibleSection>
  );
}
