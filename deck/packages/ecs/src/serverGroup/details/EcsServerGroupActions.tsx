import React from 'react';
import { Dropdown, Tooltip } from 'react-bootstrap';

import { AWSProviderSettings } from '@spinnaker/amazon';
import type { IOwnerOption, IRouterInjectedProps, IServerGroupActionsProps, IServerGroupJob } from '@spinnaker/core';
import {
  AddEntityTagLinks,
  ClusterTargetBuilder,
  ConfirmationModalService,
  ManagedMenuItem,
  ServerGroupWarningMessageService,
  SETTINGS,
  useDeckRuntimeServices,
  withRouter,
} from '@spinnaker/core';

import { EcsResizeServerGroupModal } from './resize/EcsResizeServerGroupModal';
import { EcsNativeRollbackServerGroupModal } from './rollback/EcsNativeRollbackServerGroupModal';
import { EcsRollbackServerGroupModal } from './rollback/EcsRollbackServerGroupModal';

type ConfirmedAction = 'destroy' | 'disable' | 'enable';

const actionLabels: Record<ConfirmedAction, { present: string; progressive: string }> = {
  destroy: { present: 'Destroy', progressive: 'Destroying' },
  disable: { present: 'Disable', progressive: 'Disabling' },
  enable: { present: 'Enable', progressive: 'Enabling' },
};

export function EcsServerGroupActionsComponent({
  app,
  serverGroup,
  stateService,
}: IServerGroupActionsProps & IRouterInjectedProps) {
  const runtimeServices = useDeckRuntimeServices();
  const { serverGroupWriter } = runtimeServices;
  if (!AWSProviderSettings.adHocInfraWritesEnabled) {
    return null;
  }

  const showEntityTags = SETTINGS.feature && SETTINGS.feature.entityTags;
  // The classic rollback re-enables a disabled sibling server group; an ecs-native service is a
  // single durable service, so it rolls back to an earlier task-definition revision instead.
  //
  // We can't key off cloudProvider/type: clouddriver's read path (EcsServerClusterProvider) stamps
  // both as "ecs" on every ECS server group, native ones included. Instead clouddriver sets an
  // explicit `isNative` flag on the payload (via EcsServerGroup.getExtraAttributes), derived from
  // the durable `spinnaker:provider=ecs-native` ECS service tag that ecs-native deploys write. The
  // service name is not ownership evidence (externally created services can be unversioned too),
  // and rollout fields (taskDefinitionRevision / rolloutState) are NOT a reliable signal either --
  // classic ECS services carry those too.
  // Native Continue/Stop stages reference the deploy stage, from which Orca resolves the exact
  // deployment ARN; the details view does not derive one from the service payload.
  const isNative = (serverGroup as any).isNative === true;
  const openRollback = () =>
    isNative
      ? EcsNativeRollbackServerGroupModal.show({ application: app, serverGroup }, runtimeServices)
      : EcsRollbackServerGroupModal.show({ application: app, serverGroup }, runtimeServices);
  const entityTagTargets: IOwnerOption[] = ClusterTargetBuilder.buildClusterTargets(serverGroup);
  const enableLocked =
    serverGroup.isDisabled &&
    (serverGroup.runningTasks || []).some((task: any) =>
      (task.execution?.stages || []).some((stage: any) => stage.type === 'resizeServerGroup'),
    );

  const confirmAction = (action: ConfirmedAction): void => {
    const label = actionLabels[action];
    const stateParams = { name: serverGroup.name, accountId: serverGroup.account, region: serverGroup.region };
    const confirmationModalParams = {
      header: `Really ${action} ${serverGroup.name}?`,
      buttonText: `${label.present} ${serverGroup.name}`,
      account: serverGroup.account,
      taskMonitorConfig: {
        application: app,
        title: `${label.progressive} ${serverGroup.name}`,
        ...(action === 'destroy'
          ? {
              onTaskComplete: () => {
                if (stateService.includes('**.serverGroup', stateParams)) {
                  stateService.go('^');
                }
              },
            }
          : {}),
      },
      platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
      platformHealthType: 'Ecs',
      interestingHealthProviderNames:
        app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly ? ['Ecs'] : undefined,
      submitMethod: (params: IServerGroupJob) => {
        if (action === 'destroy') {
          return serverGroupWriter.destroyServerGroup(serverGroup, app, params);
        }
        if (action === 'disable') {
          return serverGroupWriter.disableServerGroup(serverGroup, app.name, params);
        }
        return serverGroupWriter.enableServerGroup(serverGroup, app, params);
      },
      askForReason: true,
    };

    if (action === 'destroy') {
      ServerGroupWarningMessageService.addDestroyWarningMessage(app, serverGroup, confirmationModalParams);
    } else if (action === 'disable') {
      ServerGroupWarningMessageService.addDisableWarningMessage(app, serverGroup, confirmationModalParams);
    }

    ConfirmationModalService.confirm(confirmationModalParams);
  };

  return (
    <Dropdown className="dropdown" id="server-group-actions-dropdown">
      <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">Server Group Actions</Dropdown.Toggle>
      <Dropdown.Menu className="dropdown-menu">
        {!serverGroup.isDisabled && (
          <ManagedMenuItem resource={serverGroup} application={app} onClick={openRollback}>
            Rollback
          </ManagedMenuItem>
        )}
        <ManagedMenuItem
          resource={serverGroup}
          application={app}
          onClick={() => EcsResizeServerGroupModal.show({ application: app, serverGroup }, runtimeServices)}
        >
          Resize
        </ManagedMenuItem>
        {!serverGroup.isDisabled && (
          <ManagedMenuItem resource={serverGroup} application={app} onClick={() => confirmAction('disable')}>
            Disable
          </ManagedMenuItem>
        )}
        {serverGroup.isDisabled && !enableLocked && (
          <ManagedMenuItem resource={serverGroup} application={app} onClick={() => confirmAction('enable')}>
            Enable
          </ManagedMenuItem>
        )}
        {enableLocked && (
          <li className="disabled">
            <Tooltip
              id="ecs-enable-locked-tooltip"
              value="Cannot enable this server group until resize operation completes"
              placement="left"
            >
              <a>
                <span className="small glyphicon glyphicon-lock" /> Enable
              </a>
            </Tooltip>
          </li>
        )}
        <ManagedMenuItem resource={serverGroup} application={app} onClick={() => confirmAction('destroy')}>
          Destroy
        </ManagedMenuItem>
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
  );
}

export const EcsServerGroupActions = withRouter(EcsServerGroupActionsComponent);
