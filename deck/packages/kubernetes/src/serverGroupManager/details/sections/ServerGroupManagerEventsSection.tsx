import React from 'react';

import { CollapsibleSection } from '@spinnaker/core';

import type { IKubernetesServerGroupManagerDetailsSectionProps } from './IKubernetesServerGroupManagerDetailsSectionProps';
import { ManifestEvents } from '../../../pipelines/stages/deployManifest/manifestStatus/ManifestEvents';

export function ServerGroupManagerEventsSection({ manifest }: IKubernetesServerGroupManagerDetailsSectionProps) {
  return (
    <CollapsibleSection heading="Events" defaultExpanded={true}>
      <ManifestEvents manifest={manifest} />
    </CollapsibleSection>
  );
}
