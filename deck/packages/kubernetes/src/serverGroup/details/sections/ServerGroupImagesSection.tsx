import React from 'react';

import { CollapsibleSection } from '@spinnaker/core';
import type { IKubernetesServerGroupDetailsSectionProps } from './IKubernetesServerGroupDetailsSectionProps';
import { ManifestImageDetails } from '../../../manifest/ManifestImageDetails';

export function ServerGroupImagesSection({ serverGroup }: IKubernetesServerGroupDetailsSectionProps) {
  const { manifest } = serverGroup;
  return (
    <CollapsibleSection heading="Images" defaultExpanded={true}>
      <ManifestImageDetails manifest={manifest.manifest} />
    </CollapsibleSection>
  );
}
