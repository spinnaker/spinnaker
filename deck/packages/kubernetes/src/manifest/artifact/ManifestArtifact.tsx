import React from 'react';

import type { IArtifact } from '@spinnaker/core';

export interface IKubernetesManifestArtifactProps {
  artifact: IArtifact;
}

export function ManifestArtifact({ artifact }: IKubernetesManifestArtifactProps) {
  return (
    <span>
      <b>{artifact.type}&nbsp;</b>
      <i>{artifact.reference}</i>
    </span>
  );
}
