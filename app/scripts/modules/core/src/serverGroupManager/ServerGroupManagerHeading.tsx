import * as React from 'react';

import { CloudProviderLogo, HealthCounts, IInstanceCounts } from 'core';

export interface IServerGroupManagerHeadingProps {
  health: IInstanceCounts;
  provider: string;
  onClick(event: React.MouseEvent<HTMLElement>): void;
}

export const ServerGroupManagerHeading = ({ onClick, health, provider }: IServerGroupManagerHeadingProps) => {
  return (
    <div className={`flex-container-h baseline server-group-title`} onClick={onClick}>
      <div className="flex-container-h baseline section-title">
        <CloudProviderLogo provider={provider} height="16px" width="16px" />
      </div>
      <div className="flex-container-h baseline flex-pull-right">
        <HealthCounts container={health} />
      </div>
    </div>
  );
};
