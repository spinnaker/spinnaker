import * as React from 'react';

import { ICiBuild } from '../domain';

interface IBuildInfoArtifactsTabProps {
  build: ICiBuild;
}

export function BuildInfoArtifactsTab({ build }: IBuildInfoArtifactsTabProps) {
  return (
    <div className="flex-container-v">
      {build.artifacts?.map((artifact) => (
        <a href={artifact.url} key={artifact.name} target="_blank">
          {decodeURIComponent(artifact.name)}
        </a>
      ))}
    </div>
  );
}
