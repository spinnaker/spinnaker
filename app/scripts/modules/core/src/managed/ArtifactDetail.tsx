import React from 'react';

import { IManagedArtifactVersion } from '../domain';
import { useEventListener } from '../presentation';

import { ArtifactDetailHeader } from './ArtifactDetailHeader';

import './ArtifactDetail.less';

export interface IArtifactDetailProps {
  name: string;
  version: IManagedArtifactVersion;
  onRequestClose: () => any;
}

export const ArtifactDetail = ({ name, version, onRequestClose }: IArtifactDetailProps) => {
  const keydownCallback = ({ keyCode }: KeyboardEvent) => {
    if (keyCode === 27 /* esc */) {
      onRequestClose();
    }
  };
  useEventListener(document, 'keydown', keydownCallback);

  return (
    <>
      <ArtifactDetailHeader name={name} version={version.version} onRequestClose={onRequestClose} />

      <div className="ArtifactDetail">
        <div className="flex-container-h">
          {/* a short summary with actions/buttons will live here */}
          <div className="detail-section-right">{/* artifact metadata will live here */}</div>
        </div>
      </div>
    </>
  );
};
