import React from 'react';

import type { IManifestStatus } from '@spinnaker/core';
import { Spinner, Tooltip } from '@spinnaker/core';

export interface IKubernetesManifestStatusProps {
  status?: { [key: string]: IManifestStatus };
}

interface IStatusTooltipProps {
  message?: string;
  children: React.ReactNode;
}

// Status messages can come from any controller that writes the resource's status, so show them as
// plain text rather than through Tooltip's Markdown rendering.
function StatusTooltip({ message, children }: IStatusTooltipProps) {
  return <Tooltip template={message ? <span>{message}</span> : undefined}>{children}</Tooltip>;
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
        <StatusTooltip message={status.available.message}>
          <div className="band band-warning">Not Fully Available</div>
        </StatusTooltip>
      )}
      {!status.stable.state && (
        <StatusTooltip message={status.stable.message}>
          <div className="band band-active">Transitioning</div>
        </StatusTooltip>
      )}
      {status.paused.state && (
        <StatusTooltip message={status.paused.message}>
          <div className="band band-info">Rollout Paused</div>
        </StatusTooltip>
      )}
    </div>
  );
}
