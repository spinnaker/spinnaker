import React from 'react';

import type { Application } from '@spinnaker/core';
import { ConfirmationModalService, ManifestWriter } from '@spinnaker/core';

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
}

export function RollingRestart({ application, serverGroupManager }: IRollingRestartProps) {
  function rollingRestart() {
    const rollingRestartParameters: IRollingRestartParameters = {
      account: serverGroupManager.account,
      cloudProvider: 'kubernetes',
      location: serverGroupManager.namespace,
      manifestName: serverGroupManager.name,
    };
    ConfirmationModalService.confirm({
      account: serverGroupManager.account,
      askForReason: true,
      header: `Initiate rolling restart of ${serverGroupManager.name}`,
      submitMethod: () => {
        return ManifestWriter.rollingRestartManifest(rollingRestartParameters, application);
      },
      taskMonitorConfig: {
        application,
        title: `Rolling restart of ${serverGroupManager.name}`,
      },
    });
  }
  return (
    <li>
      <a onClick={rollingRestart}>Rolling Restart</a>
    </li>
  );
}
