import React from 'react';

import { CollapsibleSection } from '@spinnaker/core';

import type { IKubernetesServerGroupManagerDetailsSectionProps } from './IKubernetesServerGroupManagerDetailsSectionProps';
import type { IKubernetesManifestCondition } from '../../../manifest/status/ManifestCondition';
import { ManifestCondition } from '../../../manifest/status/ManifestCondition';

export function ServerGroupManagerManifestConditionSection({
  manifest,
}: IKubernetesServerGroupManagerDetailsSectionProps) {
  return (
    <CollapsibleSection heading="Status" defaultExpanded={true}>
      {manifest.manifest.status.conditions.map((condition: IKubernetesManifestCondition) => (
        <ul key={condition.lastTransitionTime}>
          <ManifestCondition condition={condition} />
        </ul>
      ))}
    </CollapsibleSection>
  );
}
