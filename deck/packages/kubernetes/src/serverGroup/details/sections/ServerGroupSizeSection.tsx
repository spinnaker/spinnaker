import React from 'react';

import { CollapsibleSection } from '@spinnaker/core';
import type { IKubernetesServerGroupDetailsSectionProps } from './IKubernetesServerGroupDetailsSectionProps';

export function ServerGroupSizeSection({ serverGroup }: IKubernetesServerGroupDetailsSectionProps) {
  return (
    <CollapsibleSection heading="Size" defaultExpanded={true}>
      {serverGroup.capacity.min == null && (
        <dl className="dl-horizontal dl-narrow">
          <dt>Current</dt>
          <dd>{serverGroup.instances.length}</dd>
        </dl>
      )}
      {serverGroup.capacity.min != null && serverGroup.capacity.min === serverGroup.capacity.max && (
        <dl className="dl-horizontal dl-narrow">
          <dt>Min/Max</dt>
          <dd>{serverGroup.capacity.min}</dd>
          <dt>Current</dt>
          <dd>{serverGroup.instances.length}</dd>
        </dl>
      )}
      {serverGroup.capacity.min !== serverGroup.capacity.max && (
        <dl className="dl-horizontal dl-narrow">
          <dt>Min</dt>
          <dd>{serverGroup.capacity.min}</dd>
          <dt>Max</dt>
          <dd>{serverGroup.capacity.max}</dd>
          <dt>Current</dt>
          <dd>{serverGroup.instances.length}</dd>
        </dl>
      )}
    </CollapsibleSection>
  );
}
