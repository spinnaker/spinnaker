import React from 'react';
import { Dropdown, MenuItem } from 'react-bootstrap';

import type { Application, IManifest } from '@spinnaker/core';
import { AddEntityTagLinks, ClusterTargetBuilder, IfFeatureEnabled, robotToHuman, SETTINGS } from '@spinnaker/core';

import type { IKubernetesServerGroupManager } from '../../interfaces';
import { KubernetesManifestCommandBuilder } from '../../manifest';
import { Delete } from '../../manifest/delete/Delete';
import { PauseRollout } from '../../manifest/rollout/PauseRollout';
import { ResumeRollout } from '../../manifest/rollout/ResumeRollout';
import { RollingRestart } from '../../manifest/rollout/RollingRestart';
import { UndoRollout } from '../../manifest/rollout/UndoRollout';
import { Scale } from '../../manifest/scale/Scale';
import { ManifestWizard } from '../../manifest/wizard/ManifestWizard';
import { useManifest } from '../../serverGroup/details/useManifest';

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
  const { manifestController } = useManifest({ manifest });

  const canUndoRolloutServerGroupManager = (): boolean => {
    return serverGroupManager && serverGroupManager.serverGroups && serverGroupManager.serverGroups.length > 0;
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

  return (
    <>
      <Dropdown className="dropdown" id="server-group-manager-actions-dropdown">
        {isEnabled && (
          <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">
            {robotToHuman(serverGroupManager.kind)} Actions
          </Dropdown.Toggle>
        )}
        <Dropdown.Menu>
          <Scale
            application={application}
            resource={serverGroupManager}
            currentReplicas={manifest.manifest.spec.replicas}
          />
          {canUndoRolloutServerGroupManager() && (
            <UndoRollout application={application} resource={serverGroupManager} />
          )}
          {manifest.status.paused.state && <ResumeRollout application={application} resource={serverGroupManager} />}
          {!manifest.status.paused.state && !manifest.status.stable.state && (
            <PauseRollout application={application} resource={serverGroupManager} />
          )}
          {!manifest.status.paused.state && (
            <RollingRestart application={application} serverGroupManager={serverGroupManager} />
          )}
          <MenuItem className="divider" />
          <MenuItem onClick={editServerGroupManager}>Edit</MenuItem>
          <Delete application={application} resource={serverGroupManager} manifestController={manifestController} />
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
    </>
  );
}
