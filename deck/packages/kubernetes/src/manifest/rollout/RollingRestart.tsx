import React from 'react';
import { MenuItem } from 'react-bootstrap';

import type { Application } from '@spinnaker/core';
import { ConfirmationModalService, ManifestWriter } from '@spinnaker/core';

import type { IKubernetesServerGroup, IKubernetesServerGroupManager } from '../../interfaces';

interface IRollingRestartProps {
  application: Application;
  serverGroupManager?: IKubernetesServerGroupManager;
  serverGroup?: IKubernetesServerGroup;
}

interface IRollingRestartParameters {
  account: string;
  cloudProvider: string;
  location: string;
  manifestName: string;
  reason?: string;
}

export function RollingRestart({ application, serverGroupManager, serverGroup }: IRollingRestartProps) {
  function rollingRestart() {
    const rollingRestartParameters: IRollingRestartParameters = {
      account: serverGroupManager?.account || serverGroup?.account,
      cloudProvider: 'kubernetes',
      location: serverGroupManager?.namespace || serverGroup?.namespace,
      manifestName: serverGroupManager?.name || serverGroup?.name,
    };

    ConfirmationModalService.confirm({
      account: rollingRestartParameters.account,
      askForReason: true,
      header: `Initiate rolling restart of ${rollingRestartParameters.manifestName}`,
      submitMethod: (params: { reason?: string }) => {
        rollingRestartParameters.reason = params.reason;
        return ManifestWriter.rollingRestartManifest(rollingRestartParameters, application);
      },
      taskMonitorConfig: {
        application,
        title: `Rolling restart of ${rollingRestartParameters.manifestName} in ${rollingRestartParameters.account}`,
        onTaskComplete: () => application.serverGroups.refresh(true),
      },
    });
  }
  return <MenuItem onClick={rollingRestart}>Rolling Restart</MenuItem>;
}
