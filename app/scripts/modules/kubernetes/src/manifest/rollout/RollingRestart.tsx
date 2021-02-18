import { module } from 'angular';
import React from 'react';
import { react2angular } from 'react2angular';

import { Application, ConfirmationModalService, ManifestWriter, withErrorBoundary } from '@spinnaker/core';

import { IKubernetesServerGroupManager } from '../../interfaces';

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

function RollingRestart({ application, serverGroupManager }: IRollingRestartProps) {
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

export const KUBERNETES_ROLLING_RESTART = 'spinnaker.kubernetes.v2.rolling.restart';
module(KUBERNETES_ROLLING_RESTART, []).component(
  'kubernetesRollingRestart',
  react2angular(withErrorBoundary(RollingRestart, 'kubernetesRollingRestart'), ['application', 'serverGroupManager']),
);
