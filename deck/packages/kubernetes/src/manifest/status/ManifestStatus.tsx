import React from 'react';

import type { IManifestStatus } from '@spinnaker/core';
import { Spinner, Tooltip } from '@spinnaker/core';

export interface IKubernetesManifestStatusProps {
  status?: { [key: string]: IManifestStatus };
}

export function ManifestStatus({ status }: IKubernetesManifestStatusProps) {
  if (!status) {
    return (
      <div className="header">
        <div className="horizontal middle center spinner-section">
          <Spinner size="small" />
        </div>
      </div>
    );
  }
  return (
    <div>
      {!status.available.state && (
        <Tooltip value={status.available.message}>
          <div className="band band-warning">Not Fully Available</div>
        </Tooltip>
      )}
      {!status.stable.state && (
        <Tooltip value={status.stable.message}>
          <div className="band band-active">Transitioning</div>
        </Tooltip>
      )}
      {status.paused.state && (
        <Tooltip value={status.paused.message}>
          <div className="band band-info">Rollout Paused</div>
        </Tooltip>
      )}
    </div>
  );
}
