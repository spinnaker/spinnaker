import React from 'react';
import { Dropdown, MenuItem } from 'react-bootstrap';

import type { IOwnerOption, IServerGroupActionsProps } from '@spinnaker/core';
import { ConfirmationModalService } from '@spinnaker/core';
import { AddEntityTagLinks, ClusterTargetBuilder, robotToHuman, SETTINGS } from '@spinnaker/core';

import type { IKubernetesServerGroupView } from '../../interfaces';
import { KubernetesManifestCommandBuilder } from '../../manifest';
import { Delete } from '../../manifest/delete/Delete';
import { RollingRestart } from '../../manifest/rollout/RollingRestart';
import { Scale } from '../../manifest/scale/Scale';
import { ManifestTrafficService } from '../../manifest/traffic/ManifestTrafficService';
import { ManifestWizard } from '../../manifest/wizard/ManifestWizard';
import { useManifest } from './useManifest';

export interface IKubernetesServerGroupActionsProps extends IServerGroupActionsProps {
  serverGroup: IKubernetesServerGroupView;
}

export function KubernetesServerGroupActions({ app, serverGroup }: IKubernetesServerGroupActionsProps) {
  const showEntityTags = SETTINGS.feature && SETTINGS.feature.entityTags;
  const entityTagTargets: IOwnerOption[] = ClusterTargetBuilder.buildClusterTargets(serverGroup);
  const { manifest } = serverGroup;
  const { manifestController } = useManifest({ manifest });

  const canScaleServerGroup = (): boolean => {
    return serverGroup.kind !== 'daemonSet' && manifestController === null;
  };

  const canEditServerGroup = (): boolean => {
    return manifestController === null;
  };

  const editServerGroup = (): void => {
    KubernetesManifestCommandBuilder.buildNewManifestCommand(
      app,
      manifest.manifest,
      serverGroup.moniker,
      serverGroup.account,
    ).then((builtCommand) => {
      ManifestWizard.show({ title: 'Edit Manifest', application: app, command: builtCommand });
    });
  };

  const canDisable = () => ManifestTrafficService.canDisableServerGroup(serverGroup);

  const disableServerGroup = (): void => {
    ConfirmationModalService.confirm({
      header: `Really disable ${manifest.name}?`,
      buttonText: 'Disable',
      askForReason: true,
      submitJustWithReason: true,
      submitMethod: ({ reason }: { reason: string }) => ManifestTrafficService.disable(manifest, app, reason),
      taskMonitorConfig: {
        application: app,
        title: `Disabling ${manifest.name}`,
        onTaskComplete: () => app.getDataSource('serverGroups').refresh(),
      },
    });
  };

  const canEnable = () => ManifestTrafficService.canEnableServerGroup(serverGroup);

  const enableServerGroup = (): void => {
    ConfirmationModalService.confirm({
      header: `Really enable ${manifest.name}?`,
      buttonText: 'Enable',
      askForReason: true,
      submitJustWithReason: true,
      submitMethod: ({ reason }: { reason: string }) => ManifestTrafficService.enable(manifest, app, reason),
      taskMonitorConfig: {
        application: app,
        title: `Enabling ${manifest.name}`,
        onTaskComplete: () => app.getDataSource('serverGroups').refresh(),
      },
    });
  };

  return (
    <>
      <Dropdown className="dropdown" id="server-group-actions-dropdown">
        <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">
          {robotToHuman(serverGroup.kind)} Actions
        </Dropdown.Toggle>
        <Dropdown.Menu>
          {canEditServerGroup() && <MenuItem onClick={editServerGroup}>Edit</MenuItem>}
          {canScaleServerGroup() && (
            <Scale application={app} resource={serverGroup} currentReplicas={manifest.manifest.spec.replicas} />
          )}
          {canEnable() && <MenuItem onClick={enableServerGroup}>Enable</MenuItem>}
          {canDisable() && <MenuItem onClick={disableServerGroup}>Disable</MenuItem>}
          <Delete application={app} resource={serverGroup} manifestController={manifestController} />
          {!manifest.status.paused.state && <RollingRestart application={app} serverGroup={serverGroup} />}
          {showEntityTags && (
            <AddEntityTagLinks
              component={serverGroup}
              application={app}
              entityType="serverGroup"
              ownerOptions={entityTagTargets}
              onUpdate={() => app.serverGroups.refresh()}
            />
          )}
        </Dropdown.Menu>
      </Dropdown>
    </>
  );
}
