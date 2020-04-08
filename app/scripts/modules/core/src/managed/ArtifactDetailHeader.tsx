import React from 'react';

import { Icon } from '../presentation';
import { IManagedArtifactVersion } from '../domain';

import { getArtifactVersionDisplayName } from './displayNames';

import './ArtifactDetailHeader.less';

export interface IArtifactDetailHeaderProps {
  name: string;
  version: IManagedArtifactVersion;
  onRequestClose: () => any;
}

export const ArtifactDetailHeader = ({ name, version, onRequestClose }: IArtifactDetailHeaderProps) => (
  <div className="ArtifactDetailHeader flex-container-h space-between middle text-bold">
    <div className="header-section-left flex-container-h middle">
      <Icon name="artifact" appearance="light" size="extraLarge" />
      <span className="header-version-pill">{getArtifactVersionDisplayName(version)}</span>
    </div>

    <div className="header-section-center">{name}</div>

    <div className="header-close flex-container-h center middle" onClick={() => onRequestClose()}>
      <Icon name="close" appearance="light" size="medium" />
    </div>
  </div>
);
