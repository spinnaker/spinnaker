import * as React from 'react';
import { IArtifact, ArtifactIconService } from '@spinnaker/core';

export interface IArtifactIconListProps {
  artifacts: IArtifact[];
}

export const ArtifactIconList = (props: IArtifactIconListProps): any => {
  return props.artifacts.map(artifact => {
    const iconPath = ArtifactIconService.getPath(artifact.type);
    return (
      <div key={artifact.reference} className="artifact-list-item" title={artifact.type}>
        {iconPath && <img className="artifact-list-item-icon" width="20" height="20" src={iconPath} />}
        <span className="artifact-list-item-name">
          {artifact.name}
          {artifact.version && <span> - {artifact.version}</span>}
        </span>
      </div>
    );
  });
};
