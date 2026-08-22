import { get } from 'lodash';
import React from 'react';
import { Dropdown, MenuItem, Tooltip } from 'react-bootstrap';

import type { IOwnerOption, IRouterInjectedProps, IServerGroupActionsProps, IServerGroupJob } from '@spinnaker/core';
import {
  AddEntityTagLinks,
  ClusterTargetBuilder,
  ConfirmationModalService,
  DeckRuntimeContext,
  ManagedMenuItem,
  Overridable,
  ServerGroupWarningMessageService,
  SETTINGS,
  withRouter,
} from '@spinnaker/core';

import { AWSProviderSettings } from '../../aws.settings';
import type { IAmazonServerGroupCommand } from '../configure';
import { AmazonCloneServerGroupModal } from '../configure/wizard/AmazonCloneServerGroupModal';
import type { IAmazonServerGroup, IAmazonServerGroupView } from '../../domain';
import type { IAmazonResizeServerGroupModalProps } from './resize/AmazonResizeServerGroupModal';
import { AmazonResizeServerGroupModal } from './resize/AmazonResizeServerGroupModal';
import {
  AmazonRollbackServerGroupModal,
  isAmazonRollbackAvailable,
  selectAmazonRollbackServerGroups,
} from './rollback';

export interface IAmazonServerGroupActionsProps extends IServerGroupActionsProps {
  serverGroup: IAmazonServerGroupView;
}

@Overridable('AmazonServerGroupActions.resize')
export class AmazonServerGroupActionsResize extends React.Component<IAmazonResizeServerGroupModalProps> {
  public static contextType = DeckRuntimeContext;
  public declare context: React.ContextType<typeof DeckRuntimeContext>;

  private resizeServerGroup = (): void => {
    AmazonResizeServerGroupModal.show(this.props, this.context.services);
  };

  public render(): JSX.Element {
    return <MenuItem onClick={this.resizeServerGroup}>Resize</MenuItem>;
  }
}

export class AmazonServerGroupActionsComponent extends React.Component<
  IAmazonServerGroupActionsProps & IRouterInjectedProps
> {
  public static contextType = DeckRuntimeContext;
  public declare context: React.ContextType<typeof DeckRuntimeContext>;

  private isEnableLocked(): boolean {
    if (this.props.serverGroup.isDisabled) {
      const resizeTasks = (this.props.serverGroup.runningTasks || []).filter((task) =>
        get(task, 'execution.stages', []).some((stage) => stage.type === 'resizeServerGroup'),
      );
      if (resizeTasks.length) {
        return true;
      }
    }
    return false;
  }

  private isRollbackEnabled(): boolean {
    const { app, serverGroup } = this.props;
    return isAmazonRollbackAvailable(app.name, serverGroup, app.getDataSource('serverGroups').data);
  }

  private hasDisabledInstances(): boolean {
    // server group may have disabled instances (out of service) but NOT itself be disabled
    return this.props.serverGroup.isDisabled || get(this.props.serverGroup, 'instanceCounts.outOfService', 0) > 0;
  }

  private destroyServerGroup = (): void => {
    const { app, serverGroup } = this.props;

    const taskMonitor = {
      application: app,
      title: 'Destroying ' + serverGroup.name,
      onTaskComplete: () => {
        if (this.props.stateService.includes('**.serverGroup', stateParams)) {
          this.props.stateService.go('^');
        }
      },
    };

    const submitMethod = (params: IServerGroupJob) =>
      this.context.services.serverGroupWriter.destroyServerGroup(serverGroup, app, params);

    const stateParams = {
      name: serverGroup.name,
      accountId: serverGroup.account,
      region: serverGroup.region,
    };

    const confirmationModalParams = {
      header: 'Really destroy ' + serverGroup.name + '?',
      buttonText: 'Destroy ' + serverGroup.name,
      account: serverGroup.account,
      taskMonitorConfig: taskMonitor,
      interestingHealthProviderNames: undefined as string[],
      submitMethod,
      askForReason: true,
      platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
      platformHealthType: 'Amazon',
    };

    ServerGroupWarningMessageService.addDestroyWarningMessage(app, serverGroup, confirmationModalParams);

    if (app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
      confirmationModalParams.interestingHealthProviderNames = ['Amazon'];
    }

    ConfirmationModalService.confirm(confirmationModalParams);
  };

  private disableServerGroup = (): void => {
    const { app, serverGroup } = this.props;

    const taskMonitor = {
      application: app,
      title: 'Disabling ' + serverGroup.name,
    };

    const submitMethod = (params: IServerGroupJob) => {
      return this.context.services.serverGroupWriter.disableServerGroup(serverGroup, app.name, params);
    };

    const confirmationModalParams = {
      header: 'Really disable ' + serverGroup.name + '?',
      buttonText: 'Disable ' + serverGroup.name,
      account: serverGroup.account,
      interestingHealthProviderNames: undefined as string[],
      taskMonitorConfig: taskMonitor,
      platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
      platformHealthType: 'Amazon',
      submitMethod,
      askForReason: true,
    };

    ServerGroupWarningMessageService.addDisableWarningMessage(app, serverGroup, confirmationModalParams);

    if (app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
      confirmationModalParams.interestingHealthProviderNames = ['Amazon'];
    }

    ConfirmationModalService.confirm(confirmationModalParams);
  };

  private enableServerGroup = (): void => {
    if (!this.isRollbackEnabled()) {
      this.showEnableServerGroupModal();
      return;
    }

    const confirmationModalParams = {
      header: 'Rolling back?',
      body: `Spinnaker provides an orchestrated rollback feature to carefully restore a different version of this
             server group. Do you want to use the orchestrated rollback?`,
      buttonText: `Yes, take me to the rollback settings modal`,
      cancelButtonText: 'No, I just want to enable the server group',
    };

    ConfirmationModalService.confirm(confirmationModalParams)
      .then(() => this.rollbackServerGroup())
      .catch((error) => {
        // don't show the enable modal if the user cancels with the header button
        if (error?.source === 'footer') {
          this.showEnableServerGroupModal();
        }
      });
  };

  private showEnableServerGroupModal(): void {
    const { app, serverGroup } = this.props;

    const taskMonitor = {
      application: app,
      title: 'Enabling ' + serverGroup.name,
    };

    const submitMethod = (params: IServerGroupJob) => {
      return this.context.services.serverGroupWriter.enableServerGroup(serverGroup, app, params);
    };

    const confirmationModalParams = {
      header: 'Really enable ' + serverGroup.name + '?',
      buttonText: 'Enable ' + serverGroup.name,
      account: serverGroup.account,
      interestingHealthProviderNames: undefined as string[],
      taskMonitorConfig: taskMonitor,
      platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
      platformHealthType: 'Amazon',
      submitMethod,
      askForReason: true,
    };

    if (app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
      confirmationModalParams.interestingHealthProviderNames = ['Amazon'];
    }

    ConfirmationModalService.confirm(confirmationModalParams);
  }

  private rollbackServerGroup = (): void => {
    const { app, serverGroup } = this.props;
    const selection = selectAmazonRollbackServerGroups(
      app.name,
      serverGroup,
      app.getDataSource('serverGroups').data as IAmazonServerGroup[],
    );

    if (selection) {
      AmazonRollbackServerGroupModal.show({ application: app, ...selection }, this.context.services);
    }
  };

  private cloneServerGroup = (): void => {
    const { app, serverGroup } = this.props;
    this.context.services.serverGroupCommandBuilder
      .buildServerGroupCommandFromExisting(app, serverGroup)
      .then((command: IAmazonServerGroupCommand) => {
        const title = `Clone ${serverGroup.name}`;
        AmazonCloneServerGroupModal.show({ title, application: app, command }, this.context.services);
      });
  };

  public render(): JSX.Element {
    const { app, serverGroup } = this.props;

    const showEntityTags = SETTINGS.feature && SETTINGS.feature.entityTags;
    const entityTagTargets: IOwnerOption[] = ClusterTargetBuilder.buildClusterTargets(serverGroup);

    return (
      <>
        {AWSProviderSettings.adHocInfraWritesEnabled && (
          <Dropdown className="dropdown" id="server-group-actions-dropdown">
            <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">Server Group Actions</Dropdown.Toggle>
            <Dropdown.Menu className="dropdown-menu">
              {this.isRollbackEnabled() && (
                <ManagedMenuItem resource={serverGroup} application={app} onClick={this.rollbackServerGroup}>
                  Rollback
                </ManagedMenuItem>
              )}
              {this.isRollbackEnabled() && <li role="presentation" className="divider" />}
              <AmazonServerGroupActionsResize application={app} serverGroup={serverGroup} />
              {!serverGroup.isDisabled && (
                <ManagedMenuItem resource={serverGroup} application={app} onClick={this.disableServerGroup}>
                  Disable
                </ManagedMenuItem>
              )}
              {this.hasDisabledInstances() && !this.isEnableLocked() && (
                <ManagedMenuItem resource={serverGroup} application={app} onClick={this.enableServerGroup}>
                  Enable
                </ManagedMenuItem>
              )}
              {this.isEnableLocked() && (
                <li className="disabled">
                  <Tooltip value="Cannot enable this server group until resize operation completes" placement="left">
                    <a>
                      <span className="small glyphicon glyphicon-lock" /> Enable
                    </a>
                  </Tooltip>
                </li>
              )}
              <ManagedMenuItem resource={serverGroup} application={app} onClick={this.destroyServerGroup}>
                Destroy
              </ManagedMenuItem>
              <li>
                <a className="clickable" onClick={this.cloneServerGroup}>
                  Clone
                </a>
              </li>
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
        )}
      </>
    );
  }
}

export const AmazonServerGroupActions = withRouter(AmazonServerGroupActionsComponent);
