import React from 'react';

import { CollapsibleSection } from '@spinnaker/core';

import type { IKubernetesServerGroupManagerDetailsSectionProps } from './IKubernetesServerGroupManagerDetailsSectionProps';
import { ManifestLabels } from '../../../manifest/ManifestLabels';

export function ServerGroupManagerLabelsSection({ manifest }: IKubernetesServerGroupManagerDetailsSectionProps) {
  return (
    <CollapsibleSection heading="Labels" defaultExpanded={true}>
      <ManifestLabels manifest={manifest.manifest} />
    </CollapsibleSection>
  );
}
