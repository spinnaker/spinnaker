import { filter, find, get, orderBy } from 'lodash';
import React from 'react';
import { Dropdown, Tooltip } from 'react-bootstrap';

import {
  AddEntityTagLinks,
  ClusterTargetBuilder,
  ConfirmationModalService,
  IOwnerOption,
  IServerGroupActionsProps,
  IServerGroupJob,
  ReactInjector,
  ServerGroupWarningMessageService,
  SETTINGS,
} from '@spinnaker/core';

import { CloudFoundryServerGroupCommandBuilder } from '../configure';
import { CloudFoundryCreateServerGroupModal } from '../configure/wizard/CreateServerGroupModal';
import { ICloudFoundryServerGroup } from '../../domain';
import { CloudFoundryMapLoadBalancersModal } from './mapLoadBalancers/CloudFoundryMapLoadBalancersModal';
import { CloudFoundryUnmapLoadBalancersModal } from './mapLoadBalancers/CloudFoundryUnmapLoadBalancersModal';
import { CloudFoundryResizeServerGroupModal } from './resize/CloudFoundryResizeServerGroupModal';
import { CloudFoundryRollbackServerGroupModal } from './rollback/CloudFoundryRollbackServerGroupModal';

export interface ICloudFoundryServerGroupActionsProps extends IServerGroupActionsProps {
  serverGroup: ICloudFoundryServerGroup;
}

export interface ICloudFoundryServerGroupJob extends IServerGroupJob {
  serverGroupId: string;
}

export class CloudFoundryServerGroupActions extends React.Component<ICloudFoundryServerGroupActionsProps> {
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
        (g: ICloudFoundryServerGroup) =>
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

    const stateParams = {
      name: serverGroup.name,
      accountId: serverGroup.account,
      region: serverGroup.region,
    };

    const taskMonitor = {
      application: app,
      title: 'Destroying ' + serverGroup.name,
      onTaskComplete: () => {
        if (ReactInjector.$state.includes('**.serverGroup', stateParams)) {
          ReactInjector.$state.go('^');
        }
      },
    };

    const submitMethod = (params: ICloudFoundryServerGroupJob) => {
      params.serverGroupName = serverGroup.name;
      return ReactInjector.serverGroupWriter.destroyServerGroup(serverGroup, app, params);
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
      platformHealthType: 'Cloud Foundry',
    };

    ServerGroupWarningMessageService.addDestroyWarningMessage(app, serverGroup, confirmationModalParams);

    if (app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
      confirmationModalParams.interestingHealthProviderNames = ['Cloud Foundry'];
    }

    ConfirmationModalService.confirm(confirmationModalParams);
  };

  private disableServerGroup = (): void => {
    const { app, serverGroup } = this.props;
    const taskMonitor = {
      application: app,
      title: 'Disabling ' + serverGroup.name,
    };

    const submitMethod = (params: ICloudFoundryServerGroupJob) => {
      params.serverGroupName = serverGroup.name;
      return ReactInjector.serverGroupWriter.disableServerGroup(serverGroup, app.name, params);
    };

    const confirmationModalParams = {
      header: 'Really disable ' + serverGroup.name + '?',
      buttonText: 'Disable ' + serverGroup.name,
      account: serverGroup.account,
      interestingHealthProviderNames: undefined as string[],
      taskMonitorConfig: taskMonitor,
      platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
      platformHealthType: 'Cloud Foundry',
      submitMethod,
      askForReason: true,
    };

    ServerGroupWarningMessageService.addDisableWarningMessage(app, serverGroup, confirmationModalParams);

    if (app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
      confirmationModalParams.interestingHealthProviderNames = ['Cloud Foundry'];
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
      buttonText: `Yes, let's start the orchestrated rollback`,
      cancelButtonText: 'No, I just want to enable the server group',
    };

    ConfirmationModalService.confirm(confirmationModalParams)
      .then(() => this.rollbackServerGroup())
      .catch(({ source }) => {
        // don't show the enable modal if the user cancels with the header button
        if (source === 'footer') {
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

    const submitMethod = (params: ICloudFoundryServerGroupJob) => {
      params.serverGroupName = serverGroup.name;
      return ReactInjector.serverGroupWriter.enableServerGroup(serverGroup, app, params);
    };

    const confirmationModalParams = {
      header: 'Really enable ' + serverGroup.name + '?',
      buttonText: 'Enable ' + serverGroup.name,
      account: serverGroup.account,
      interestingHealthProviderNames: undefined as string[],
      taskMonitorConfig: taskMonitor,
      platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
      platformHealthType: 'Cloud Foundry',
      submitMethod,
      askForReason: true,
    };

    if (app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
      confirmationModalParams.interestingHealthProviderNames = ['Cloud Foundry'];
    }

    ConfirmationModalService.confirm(confirmationModalParams);
  }

  private rollbackServerGroup = (): void => {
    const { app } = this.props;

    let serverGroup: ICloudFoundryServerGroup = this.props.serverGroup;
    let previousServerGroup: ICloudFoundryServerGroup;
    let allServerGroups = (app.serverGroups.data as ICloudFoundryServerGroup[]).filter(
      (g) => g.cluster === serverGroup.cluster && g.region === serverGroup.region && g.account === serverGroup.account,
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
        allServerGroups.filter((g: ICloudFoundryServerGroup) => g.name !== previousServerGroup.name && !g.isDisabled),
        ['instanceCounts.total', 'createdTime'],
        ['desc', 'desc'],
      )[0] as ICloudFoundryServerGroup;
    }

    // the set of all server groups should not include the server group selected for rollback
    allServerGroups = allServerGroups.filter((g) => g.name !== serverGroup.name);

    if (allServerGroups.length === 1 && !previousServerGroup) {
      // if there is only one other server group, default to it being the rollback target
      previousServerGroup = allServerGroups[0];
    }
    const cluster = find(app.clusters, {
      name: serverGroup.cluster,
      account: serverGroup.account,
      serverGroups: [],
    });
    const disabledServerGroups: ICloudFoundryServerGroup[] = filter(cluster.serverGroups, {
      isDisabled: true,
      region: serverGroup.region,
    }) as ICloudFoundryServerGroup[];

    CloudFoundryRollbackServerGroupModal.show({
      serverGroup,
      previousServerGroup,
      disabledServerGroups: disabledServerGroups.sort((a, b) => b.name.localeCompare(a.name)),
      allServerGroups: allServerGroups.sort((a, b) => b.name.localeCompare(a.name)),
      application: app,
    });
  };

  private resizeServerGroup = (): void => {
    const { app, serverGroup } = this.props;
    CloudFoundryResizeServerGroupModal.show({ application: app, serverGroup });
  };

  private mapServerGroupToLoadBalancers = (): void => {
    const { app, serverGroup } = this.props;
    CloudFoundryMapLoadBalancersModal.show({ application: app, serverGroup });
  };

  private unmapServerGroupFromLoadBalancers = (): void => {
    const { app, serverGroup } = this.props;
    CloudFoundryUnmapLoadBalancersModal.show({ application: app, serverGroup });
  };

  private cloneServerGroup = (): void => {
    const { app, serverGroup } = this.props;
    const command = CloudFoundryServerGroupCommandBuilder.buildServerGroupCommandFromExisting(app, serverGroup);
    const title = `Clone ${serverGroup.name}`;
    CloudFoundryCreateServerGroupModal.show({
      application: app,
      command,
      isSourceConstant: true,
      serverGroup,
      title,
    });
  };

  public render(): JSX.Element {
    const { app, serverGroup } = this.props;
    const { loadBalancers } = serverGroup;
    const showEntityTags = SETTINGS.feature && SETTINGS.feature.entityTags;
    const entityTagTargets: IOwnerOption[] = ClusterTargetBuilder.buildClusterTargets(serverGroup);

    return (
      <Dropdown className="dropdown" id="server-group-actions-dropdown">
        <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">Server Group Actions</Dropdown.Toggle>
        <Dropdown.Menu className="dropdown-menu">
          {this.isRollbackEnabled() && (
            <li>
              <a className="clickable" onClick={this.rollbackServerGroup}>
                Rollback
              </a>
            </li>
          )}
          {this.isRollbackEnabled() && <li role="presentation" className="divider" />}
          <li>
            <a className="clickable" onClick={this.resizeServerGroup}>
              Resize
            </a>
          </li>
          {!serverGroup.isDisabled && (
            <li>
              <a className="clickable" onClick={this.disableServerGroup}>
                Disable
              </a>
            </li>
          )}
          {this.hasDisabledInstances() && !this.isEnableLocked() && (
            <li>
              <a className="clickable" onClick={this.enableServerGroup}>
                Enable
              </a>
            </li>
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
          <li>
            <a className="clickable" onClick={this.destroyServerGroup}>
              Destroy
            </a>
          </li>
          <li>
            <a className="clickable" onClick={this.cloneServerGroup}>
              Clone
            </a>
          </li>
          {!serverGroup.isDisabled && (
            <li>
              <a className="clickable" onClick={this.mapServerGroupToLoadBalancers}>
                Map Load Balancer
              </a>
            </li>
          )}
          {!serverGroup.isDisabled && loadBalancers && !!loadBalancers.length && (
            <li>
              <a className="clickable" onClick={this.unmapServerGroupFromLoadBalancers}>
                Unmap Load Balancer
              </a>
            </li>
          )}
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
