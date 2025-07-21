import React from 'react';

import { CollapsibleSection } from '@spinnaker/core';
import type { IKubernetesServerGroupDetailsSectionProps } from './IKubernetesServerGroupDetailsSectionProps';
import { ManifestLabels } from '../../../manifest/ManifestLabels';

export function ServerGroupLabelsSection({ serverGroup }: IKubernetesServerGroupDetailsSectionProps) {
  const { manifest } = serverGroup;
  return (
    <CollapsibleSection heading="Labels" defaultExpanded={true}>
      <ManifestLabels manifest={manifest.manifest} />
    </CollapsibleSection>
  );
}
