import React from 'react';
import { MenuItem } from 'react-bootstrap';

import type { Application } from '@spinnaker/core';
import { ConfirmationModalService, ManifestWriter, robotToHuman } from '@spinnaker/core';

import type { IAnyKubernetesResource } from '../../interfaces';

export interface IResumeRolloutProps {
  application: Application;
  resource: IAnyKubernetesResource;
}

export function ResumeRollout({ application, resource }: IResumeRolloutProps) {
  const handleClick = () => {
    ConfirmationModalService.confirm({
      account: resource.account,
      askForReason: true,
      header: `Resume rollout of ${robotToHuman(resource.name)} in ${resource.namespace}`,
      submitMethod: (params: { reason?: string }) => {
        const payload = {
          cloudProvider: 'kubernetes',
          manifestName: resource.name,
          location: resource.namespace,
          account: resource.account,
          reason: params.reason,
        };
        return ManifestWriter.resumeRolloutManifest(payload, application);
      },
      taskMonitorConfig: {
        application,
        title: `Resume rollout of ${resource.name} in ${resource.namespace}`,
        onTaskComplete: () => application.serverGroups.refresh(true),
      },
    });
  };
  return <MenuItem onClick={handleClick}>Resume Rollout</MenuItem>;
}
