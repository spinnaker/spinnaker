import { filter, find, get, orderBy } from 'lodash';
import React from 'react';
import { Dropdown, MenuItem, Tooltip } from 'react-bootstrap';

import {
  AddEntityTagLinks,
  ClusterTargetBuilder,
  ConfirmationModalService,
  IOwnerOption,
  IServerGroupActionsProps,
  IServerGroupJob,
  ManagedMenuItem,
  ModalInjector,
  Overridable,
  ReactInjector,
  ServerGroupWarningMessageService,
  SETTINGS,
} from '@spinnaker/core';

import { IAmazonServerGroupCommand } from '../configure';
import { AmazonCloneServerGroupModal } from '../configure/wizard/AmazonCloneServerGroupModal';
import { IAmazonServerGroup, IAmazonServerGroupView } from '../../domain';
import { AwsReactInjector } from '../../reactShims';
import {
  AmazonResizeServerGroupModal,
  IAmazonResizeServerGroupModalProps,
} from './resize/AmazonResizeServerGroupModal';

export interface IAmazonServerGroupActionsProps extends IServerGroupActionsProps {
  serverGroup: IAmazonServerGroupView;
}

@Overridable('AmazonServerGroupActions.resize')
export class AmazonServerGroupActionsResize extends React.Component<IAmazonResizeServerGroupModalProps> {
  private resizeServerGroup = (): void => {
    AmazonResizeServerGroupModal.show(this.props);
  };

  public render(): JSX.Element {
    return <MenuItem onClick={this.resizeServerGroup}>Resize</MenuItem>;
  }
}

export class AmazonServerGroupActions extends React.Component<IAmazonServerGroupActionsProps> {
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

    if (!serverGroup.isDisabled) {
      // enabled server groups are always a candidate for rollback
      return true;
    }

    // if the server group selected for rollback is disabled, ensure that at least one enabled server group exists
    return app
      .getDataSource('serverGroups')
      .data.some(
        (g: IAmazonServerGroup) =>
          g.cluster === serverGroup.cluster &&
          g.region === serverGroup.region &&
          g.account === serverGroup.account &&
          !g.isDisabled,
      );
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
        if (ReactInjector.$state.includes('**.serverGroup', stateParams)) {
          ReactInjector.$state.go('^');
        }
      },
    };

    const submitMethod = (params: IServerGroupJob) =>
      ReactInjector.serverGroupWriter.destroyServerGroup(serverGroup, app, params);

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
      return ReactInjector.serverGroupWriter.disableServerGroup(serverGroup, app.name, params);
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
      // Wait for the confirmation modal to go away first to avoid react/angular bootstrap fighting
      // over the body.modal-open class
      .then(() => new Promise((resolve) => setTimeout(resolve, 500)))
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
      return ReactInjector.serverGroupWriter.enableServerGroup(serverGroup, app, params);
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
    const { app } = this.props;

    let serverGroup: IAmazonServerGroup = this.props.serverGroup;
    let previousServerGroup: IAmazonServerGroup;
    let allServerGroups = app
      .getDataSource('serverGroups')
      .data.filter(
        (g: IAmazonServerGroup) =>
          g.cluster === serverGroup.cluster && g.region === serverGroup.region && g.account === serverGroup.account,
      );

    if (serverGroup.isDisabled) {
      // if the selected server group is disabled, it represents the server group that should be _rolled back to_
      previousServerGroup = serverGroup;

      /*
       * Find an existing server group to rollback, prefer the largest enabled server group.
       *
       * isRollbackEnabled() ensures that at least one enabled server group exists.
       */
      serverGroup = orderBy(
        allServerGroups.filter((g: IAmazonServerGroup) => g.name !== previousServerGroup.name && !g.isDisabled),
        ['instanceCounts.total', 'createdTime'],
        ['desc', 'desc'],
      )[0] as IAmazonServerGroup;
    }

    // the set of all server groups should not include the server group selected for rollback
    allServerGroups = allServerGroups.filter((g: IAmazonServerGroup) => g.name !== serverGroup.name);

    if (allServerGroups.length === 1 && !previousServerGroup) {
      // if there is only one other server group, default to it being the rollback target
      previousServerGroup = allServerGroups[0];
    }

    ModalInjector.modalService.open({
      templateUrl: ReactInjector.overrideRegistry.getTemplate(
        'aws.rollback.modal',
        require('./rollback/rollbackServerGroup.html'),
      ),
      controller: 'awsRollbackServerGroupCtrl as ctrl',
      resolve: {
        serverGroup: () => serverGroup,
        previousServerGroup: () => previousServerGroup,
        disabledServerGroups: () => {
          const cluster = find(app.clusters, {
            name: serverGroup.cluster,
            account: serverGroup.account,
            serverGroups: [],
          });
          return filter(cluster.serverGroups, { isDisabled: true, region: serverGroup.region });
        },
        allServerGroups: () => allServerGroups,
        application: () => app,
      },
    });
  };

  private cloneServerGroup = (): void => {
    const { app, serverGroup } = this.props;
    AwsReactInjector.awsServerGroupCommandBuilder
      .buildServerGroupCommandFromExisting(app, serverGroup)
      .then((command: IAmazonServerGroupCommand) => {
        const title = `Clone ${serverGroup.name}`;
        AmazonCloneServerGroupModal.show({ title, application: app, command });
      });
  };

  public render(): JSX.Element {
    const { app, serverGroup } = this.props;

    const showEntityTags = SETTINGS.feature && SETTINGS.feature.entityTags;
    const entityTagTargets: IOwnerOption[] = ClusterTargetBuilder.buildClusterTargets(serverGroup);

    return (
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
    );
  }
}
