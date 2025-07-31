import { orderBy } from 'lodash';
import React from 'react';
import { Dropdown, MenuItem } from 'react-bootstrap';

import type { Application, IManifest } from '@spinnaker/core';
import {
  AddEntityTagLinks,
  ClusterTargetBuilder,
  IfFeatureEnabled,
  ModalInjector,
  NameUtils,
  robotToHuman,
  SETTINGS,
} from '@spinnaker/core';

import type { IKubernetesServerGroupManager } from '../../interfaces';
import { KubernetesManifestCommandBuilder } from '../../manifest';
import { RollingRestart } from '../../manifest/rollout/RollingRestart';
import { ManifestWizard } from '../../manifest/wizard/ManifestWizard';

export interface IServerGroupManagerActionsProps {
  application: Application;
  serverGroupManager: IKubernetesServerGroupManager;
  manifest: IManifest;
}

export function ServerGroupManagerActions({
  application,
  serverGroupManager,
  manifest,
}: IServerGroupManagerActionsProps) {
  const isEnabled = SETTINGS.kubernetesAdHocInfraWritesEnabled;
  const entityTagTargets = ClusterTargetBuilder.buildManagerClusterTargets(serverGroupManager);

  const pauseRolloutServerGroupManager = (): void => {
    ModalInjector.modalService.open({
      templateUrl: require('../../manifest/rollout/pause.html'),
      controller: 'kubernetesV2ManifestPauseRolloutCtrl as ctrl',
      resolve: {
        coordinates: {
          name: serverGroupManager.name,
          namespace: serverGroupManager.namespace,
          account: serverGroupManager.account,
        },
        application: application,
      },
    });
  };

  const resumeRolloutServerGroupManager = (): void => {
    ModalInjector.modalService.open({
      templateUrl: require('../../manifest/rollout/resume.html'),
      controller: 'kubernetesV2ManifestResumeRolloutCtrl as ctrl',
      resolve: {
        coordinates: {
          name: serverGroupManager.name,
          namespace: serverGroupManager.namespace,
          account: serverGroupManager.account,
        },
        application: application,
      },
    });
  };

  const canUndoRolloutServerGroupManager = (): boolean => {
    return serverGroupManager && serverGroupManager.serverGroups && serverGroupManager.serverGroups.length > 0;
  };

  const undoRolloutServerGroupManager = (): void => {
    ModalInjector.modalService.open({
      templateUrl: require('../../manifest/rollout/undo.html'),
      controller: 'kubernetesV2ManifestUndoRolloutCtrl as ctrl',
      resolve: {
        coordinates: {
          name: serverGroupManager.name,
          namespace: serverGroupManager.namespace,
          account: serverGroupManager.account,
        },
        revisions: () => {
          const [, ...rest] = orderBy(serverGroupManager.serverGroups, ['moniker.sequence'], ['desc']);
          return rest.map((serverGroup, index) => ({
            label: `${NameUtils.getSequence(serverGroup.moniker.sequence)}${index > 0 ? '' : ' - previous revision'}`,
            revision: serverGroup.moniker.sequence,
          }));
        },
        application: application,
      },
    });
  };

  const scaleServerGroupManager = () => {
    ModalInjector.modalService.open({
      templateUrl: require('../../manifest/scale/scale.html'),
      controller: 'kubernetesV2ManifestScaleCtrl as ctrl',
      resolve: {
        coordinates: {
          name: serverGroupManager.name,
          namespace: serverGroupManager.namespace,
          account: serverGroupManager.account,
        },
        currentReplicas: manifest.manifest.spec.replicas,
        application: application,
      },
    });
  };

  const editServerGroupManager = (): void => {
    KubernetesManifestCommandBuilder.buildNewManifestCommand(
      application,
      manifest.manifest,
      serverGroupManager.moniker,
      serverGroupManager.account,
    ).then((builtCommand) => {
      ManifestWizard.show({ title: 'Edit Manifest', application: application, command: builtCommand });
    });
  };

  const deleteServerGroupManager = (): void => {
    ModalInjector.modalService.open({
      templateUrl: require('../../manifest/delete/delete.html'),
      controller: 'kubernetesV2ManifestDeleteCtrl as ctrl',
      resolve: {
        coordinates: {
          name: serverGroupManager.name,
          namespace: serverGroupManager.namespace,
          account: serverGroupManager.account,
        },
        application: application,
        manifestController: (): string => null,
      },
    });
  };

  return (
    <Dropdown className="dropdown" id="server-group-manager-actions-dropdown">
      {isEnabled && (
        <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">
          {robotToHuman(serverGroupManager.kind)} Actions
        </Dropdown.Toggle>
      )}
      <Dropdown.Menu>
        <MenuItem onClick={scaleServerGroupManager}>Scale</MenuItem>
        {canUndoRolloutServerGroupManager() && (
          <MenuItem onClick={undoRolloutServerGroupManager}>Undo Rollout</MenuItem>
        )}
        {manifest.status.paused.state && <MenuItem onClick={resumeRolloutServerGroupManager}>Resume Rollout</MenuItem>}
        {!manifest.status.paused.state && !manifest.status.stable.state && (
          <MenuItem onClick={pauseRolloutServerGroupManager}>Pause Rollout</MenuItem>
        )}
        {!manifest.status.paused.state && (
          <RollingRestart application={application} serverGroupManager={serverGroupManager} />
        )}
        <MenuItem className="divider" />
        <MenuItem onClick={editServerGroupManager}>Edit</MenuItem>
        <MenuItem onClick={deleteServerGroupManager}>Delete</MenuItem>
        <IfFeatureEnabled
          feature="entityTags"
          render={
            <AddEntityTagLinks
              component={serverGroupManager}
              application={application}
              entityType="serverGroupManager"
              ownerOptions={entityTagTargets}
              onUpdate={() => application.serverGroupManagers.refresh()}
            />
          }
        />
      </Dropdown.Menu>
    </Dropdown>
  );
}
