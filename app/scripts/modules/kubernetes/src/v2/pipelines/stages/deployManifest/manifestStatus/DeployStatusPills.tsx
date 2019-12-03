import React from 'react';
import { IManifest } from '@spinnaker/core';

interface IStatusClasses {
  [key: string]: string;
}

const STATUS_PILLS: IStatusClasses = {
  available: 'success',
  stable: 'success',
  paused: 'warn',
  failed: 'danger',
};

export interface IDeployManifestStatusProps {
  manifest: IManifest;
}

export class DeployManifestStatusPills extends React.Component<IDeployManifestStatusProps> {
  public render() {
    const { manifest } = this.props;
    if (manifest == null || manifest.status == null) {
      return null;
    }
    return Object.keys(manifest.status).map((statusKey, i) => {
      const statusDescription = manifest.status[statusKey];
      const statusClass = STATUS_PILLS[statusKey] || '';
      const isStable = statusDescription.state;
      const isUnstableWithMessage = !isStable && statusDescription.message;
      return (
        <span key={i}>
          {isStable && (
            <span title={statusDescription.message} className={`pill ${statusClass}`}>
              {statusKey}
            </span>
          )}
          {isUnstableWithMessage && (
            <span title={statusDescription.message} className="pill warn">
              {statusKey}
            </span>
          )}
          {(isStable || isUnstableWithMessage) && <span>&nbsp;</span>}
        </span>
      );
    });
  }
}
