import * as React from 'react';
import { IArtifact } from 'core/domain';
import { ArtifactIconService } from 'core/artifact';

export interface IArtifactIconListProps {
  artifacts: IArtifact[];
}

export const ArtifactIconList = (props: IArtifactIconListProps): any => {
  return props.artifacts.map((artifact, i) => {
    const { location, reference, type } = artifact;
    const iconPath = ArtifactIconService.getPath(type);
    const key = `${location || ''}${type || ''}${reference || ''}` || String(i);
    return (
      <div key={key} className="artifact-list-item" title={artifact.type}>
        {iconPath && <img className="artifact-list-item-icon" width="20" height="20" src={iconPath} />}
        <span className="artifact-list-item-name">
          {artifact.name}
          {artifact.version && <span> - {artifact.version}</span>}
        </span>
      </div>
    );
  });
};
