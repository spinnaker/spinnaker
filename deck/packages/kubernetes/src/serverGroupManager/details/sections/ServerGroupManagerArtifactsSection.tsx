import React from 'react';

import type { IArtifact } from '@spinnaker/core';
import { CollapsibleSection } from '@spinnaker/core';
import type { IKubernetesServerGroupManagerDetailsSectionProps } from './IKubernetesServerGroupManagerDetailsSectionProps';
import { ManifestArtifact } from '../../../manifest';

export function ServerGroupManagerArtifactsSection({ manifest }: IKubernetesServerGroupManagerDetailsSectionProps) {
  return (
    <CollapsibleSection heading="Artifacts" defaultExpanded={true}>
      {manifest.artifacts.map((artifact: IArtifact) => (
        <ul key={artifact.reference}>
          <ManifestArtifact artifact={artifact} />
        </ul>
      ))}
    </CollapsibleSection>
  );
}
