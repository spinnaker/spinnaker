import { module } from 'angular';
import React from 'react';
import { MenuItem } from 'react-bootstrap';
import { react2angular } from 'react2angular';

import type { Application } from '@spinnaker/core';
import { ConfirmationModalService, ManifestWriter, withErrorBoundary } from '@spinnaker/core';

import type { IKubernetesServerGroupManager } from '../../interfaces';

interface IRollingRestartProps {
  application: Application;
  serverGroupManager: IKubernetesServerGroupManager;
}

interface IRollingRestartParameters {
  account: string;
  cloudProvider: string;
  location: string;
  manifestName: string;
  reason?: string;
}

export function RollingRestart({ application, serverGroupManager }: IRollingRestartProps) {
  function rollingRestart() {
    ConfirmationModalService.confirm({
      account: serverGroupManager.account,
      askForReason: true,
      header: `Initiate rolling restart of ${serverGroupManager.name}`,
      submitMethod: (params: { reason?: string }) => {
        const rollingRestartParameters: IRollingRestartParameters = {
          account: serverGroupManager.account,
          cloudProvider: 'kubernetes',
          location: serverGroupManager.namespace,
          manifestName: serverGroupManager.name,
          reason: params.reason,
        };
        return ManifestWriter.rollingRestartManifest(rollingRestartParameters, application);
      },
      taskMonitorConfig: {
        application,
        title: `Rolling restart of ${serverGroupManager.name}`,
        onTaskComplete: () => application.serverGroups.refresh(true),
      },
    });
  }
  return <MenuItem onClick={rollingRestart}>Rolling Restart</MenuItem>;
}

export const KUBERNETES_ROLLING_RESTART = 'spinnaker.kubernetes.v2.rolling.restart';
module(KUBERNETES_ROLLING_RESTART, []).component(
  'kubernetesRollingRestart',
  react2angular(withErrorBoundary(RollingRestart, 'kubernetesRollingRestart'), ['application', 'serverGroupManager']),
);
