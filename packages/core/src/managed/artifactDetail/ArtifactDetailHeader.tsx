import React from 'react';

import { Icon } from '@spinnaker/presentation';

import { getArtifactVersionDisplayName } from '../displayNames';
import { IManagedArtifactVersion } from '../../domain';

import './ArtifactDetailHeader.less';

export interface IArtifactDetailHeaderProps {
  reference?: string;
  version: IManagedArtifactVersion;
  onRequestClose: () => any;
}

export const ArtifactDetailHeader = ({ reference, version, onRequestClose }: IArtifactDetailHeaderProps) => (
  <div className="ArtifactDetailHeader flex-container-h space-between middle text-bold">
    <div className="header-section-left flex-container-h middle">
      <Icon name="artifact" appearance="light" size="extraLarge" />
      <span className="header-version-pill">{`${getArtifactVersionDisplayName(version)}${
        reference ? ' ' + reference : ''
      }`}</span>
    </div>

    <div className="header-close flex-container-h center middle" onClick={() => onRequestClose()}>
      <Icon name="close" appearance="light" size="medium" />
    </div>
  </div>
);
