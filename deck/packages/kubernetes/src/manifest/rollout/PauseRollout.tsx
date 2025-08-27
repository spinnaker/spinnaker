import { module } from 'angular';
import React from 'react';
import { MenuItem } from 'react-bootstrap';
import { react2angular } from 'react2angular';

import type { Application } from '@spinnaker/core';
import { ConfirmationModalService, ManifestWriter, robotToHuman, withErrorBoundary } from '@spinnaker/core';

import type { IAnyKubernetesResource } from '../../interfaces';

export interface IPauseRolloutProps {
  application: Application;
  resource: IAnyKubernetesResource;
}

export function PauseRollout({ application, resource }: IPauseRolloutProps) {
  const handleClick = () => {
    ConfirmationModalService.confirm({
      account: resource.account,
      askForReason: true,
      header: `Pause rollout of ${robotToHuman(resource.name)} in ${resource.namespace}`,
      submitMethod: (params: { reason?: string }) => {
        const payload = {
          cloudProvider: 'kubernetes',
          manifestName: resource.name,
          location: resource.namespace,
          account: resource.account,
          reason: params.reason,
        };
        return ManifestWriter.pauseRolloutManifest(payload, application);
      },
      taskMonitorConfig: {
        application,
        title: `Pause rollout of ${resource.name} in ${resource.namespace}`,
        onTaskComplete: () => application.serverGroups.refresh(true),
      },
    });
  };
  return <MenuItem onClick={handleClick}>Pause Rollout</MenuItem>;
}

export const KUBERNETES_PAUSE_ROLLOUT = 'spinnaker.kubernetes.pause.rollout';
module(KUBERNETES_PAUSE_ROLLOUT, []).component(
  'kubernetesPauseRollout',
  react2angular(withErrorBoundary(PauseRollout, 'kubernetesPauseRollout'), ['application', 'resource']),
);
