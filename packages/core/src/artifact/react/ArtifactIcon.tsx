import React from 'react';

import { ArtifactIconService } from '../ArtifactIconService';
import { IArtifact, IArtifactKindConfig, IExpectedArtifact } from '../../domain';
import { ExpectedArtifactService } from '../expectedArtifact.service';

export interface IArtifactIconProps {
  expectedArtifact?: IExpectedArtifact;
  artifact?: IArtifact;
  kind?: IArtifactKindConfig;
  type?: string;
  width: string | number;
  height: string | number;
}

export const ArtifactIcon = (props: IArtifactIconProps) => {
  let type: string;
  if (props.type) {
    type = props.type;
  } else if (props.artifact) {
    type = props.artifact.type;
  } else if (props.kind) {
    type = props.kind.type;
  } else if (props.expectedArtifact) {
    type = ExpectedArtifactService.artifactFromExpected(props.expectedArtifact).type;
  }
  return (
    <img
      style={{ verticalAlign: 'text-top', marginRight: '4px' }}
      src={ArtifactIconService.getPath(type)}
      width={props.width}
      height={props.height}
    />
  );
};
