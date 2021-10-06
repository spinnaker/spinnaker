import React from 'react';

import { GitLink } from './GitLink';
import type { QueryArtifactVersion } from '../types';

interface IVersionTitleProps {
  gitMetadata?: QueryArtifactVersion['gitMetadata'];
  version: string;
  buildNumber?: string;
}

export const VersionTitle = ({ gitMetadata, buildNumber }: IVersionTitleProps) => {
  return (
    <div className="VersionTitle">
      {gitMetadata ? <GitLink gitMetadata={gitMetadata} /> : <div>Build {buildNumber}</div>}
    </div>
  );
};
