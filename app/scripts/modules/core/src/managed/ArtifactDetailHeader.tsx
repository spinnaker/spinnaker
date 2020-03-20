import React from 'react';

import { Icon } from '../presentation';

import { parseName } from './Frigga';

import './ArtifactDetailHeader.less';

export interface IArtifactDetailHeaderProps {
  name: string;
  version: string;
  onRequestClose: () => any;
}

export const ArtifactDetailHeader = ({ name, version, onRequestClose }: IArtifactDetailHeaderProps) => {
  const { version: packageVersion, buildNumber } = parseName(version);

  return (
    <div className="ArtifactDetailHeader flex-container-h space-between middle text-bold">
      <div className="header-section-left flex-container-h middle">
        <Icon name="artifact" appearance="light" size="extraLarge" />
        <span className="header-version-pill">{buildNumber ? `#${buildNumber}` : packageVersion || version}</span>
      </div>

      <div className="header-section-center">{name}</div>

      <div className="header-close flex-container-h center middle" onClick={() => onRequestClose()}>
        <Icon name="close" appearance="light" size="medium" />
      </div>
    </div>
  );
};
