import React from 'react';

import { CollapsibleSection } from '@spinnaker/core';
import type { IKubernetesServerGroupDetailsSectionProps } from './IKubernetesServerGroupDetailsSectionProps';
import { ManifestEvents } from '../../../pipelines/stages/deployManifest/manifestStatus/ManifestEvents';

export function ServerGroupEventsSection({ serverGroup }: IKubernetesServerGroupDetailsSectionProps) {
  const { manifest } = serverGroup;
  return (
    <CollapsibleSection heading="Events" defaultExpanded={true}>
      <ManifestEvents manifest={manifest} />
    </CollapsibleSection>
  );
}
