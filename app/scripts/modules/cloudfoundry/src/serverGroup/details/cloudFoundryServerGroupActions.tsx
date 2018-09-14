import * as React from 'react';

import { Dropdown, Tooltip } from 'react-bootstrap';
import { get, find, filter, orderBy } from 'lodash';

import {
  ClusterTargetBuilder,
  IOwnerOption,
  IServerGroupActionsProps,
  IServerGroupJob,
  ModalInjector,
  NgReact,
  ReactInjector,
  ServerGroupWarningMessageService,
  SETTINGS,
} from '@spinnaker/core';

import { ICloudFoundryServerGroup } from 'cloudfoundry/domain';

export interface ICloudFoundryServerGroupActionsProps extends IServerGroupActionsProps {
  serverGroup: ICloudFoundryServerGroup;
}

export interface ICloudFoundryServerGroupJob extends IServerGroupJob {
  serverGroupId: string;
}

export class CloudFoundryServerGroupActions extends React.Component<ICloudFoundryServerGroupActionsProps> {
  private isEnableLocked(): boolean {
    if (this.props.serverGroup.isDisabled) {
      const resizeTasks = (this.props.serverGroup.runningTasks || []).filter(task =>
        get(task, 'execution.stages', []).some(stage => stage.type === 'resizeServerGroup'),
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

    const taskMonitor = {
      application: app,
      title: 'Destroying ' + serverGroup.name,
    };

    const submitMethod = (params: ICloudFoundryServerGroupJob) => {
      params.serverGroupName = serverGroup.name;
      return ReactInjector.serverGroupWriter.destroyServerGroup(serverGroup, app, params);
    };

    const stateParams = {
      name: serverGroup.name,
      accountId: serverGroup.account,
      region: serverGroup.region,
    };

    const confirmationModalParams = {
      header: 'Really destroy ' + serverGroup.name + '?',
      buttonText: 'Destroy ' + serverGroup.name,
      account: serverGroup.account,
      provider: 'cloudfoundry',
      taskMonitorConfig: taskMonitor,
      interestingHealthProviderNames: undefined as string[],
      submitMethod,
      askForReason: true,
      platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
      platformHealthType: 'Cloud Foundry',
      onTaskComplete: () => {
        if (ReactInjector.$state.includes('**.serverGroup', stateParams)) {
          ReactInjector.$state.go('^');
        }
      },
    };

    ServerGroupWarningMessageService.addDestroyWarningMessage(app, serverGroup, confirmationModalParams);

    if (app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
      confirmationModalParams.interestingHealthProviderNames = ['Cloud Foundry'];
    }

    ReactInjector.confirmationModalService.confirm(confirmationModalParams);
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
      provider: 'cloudfoundry',
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

    ReactInjector.confirmationModalService.confirm(confirmationModalParams);
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

    ReactInjector.confirmationModalService
      .confirm(confirmationModalParams)
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

    ReactInjector.confirmationModalService.confirm(confirmationModalParams);
  }

  private rollbackServerGroup = (): void => {
    const { app } = this.props;

    let serverGroup: ICloudFoundryServerGroup = this.props.serverGroup;
    let previousServerGroup: ICloudFoundryServerGroup;
    let allServerGroups = app
      .getDataSource('serverGroups')
      .data.filter(
        (g: ICloudFoundryServerGroup) =>
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
        allServerGroups.filter((g: ICloudFoundryServerGroup) => g.name !== previousServerGroup.name && !g.isDisabled),
        ['instanceCounts.total', 'createdTime'],
        ['desc', 'desc'],
      )[0] as ICloudFoundryServerGroup;
    }

    // the set of all server groups should not include the server group selected for rollback
    allServerGroups = allServerGroups.filter((g: ICloudFoundryServerGroup) => g.name !== serverGroup.name);

    if (allServerGroups.length === 1 && !previousServerGroup) {
      // if there is only one other server group, default to it being the rollback target
      previousServerGroup = allServerGroups[0];
    }

    ModalInjector.modalService.open({
      templateUrl: ReactInjector.overrideRegistry.getTemplate(
        'cf.rollback.modal',
        require('./rollback/rollbackServerGroup.html'),
      ),
      controller: 'cloudfoundryRollbackServerGroupCtrl as ctrl',
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

  private resizeServerGroup = (): void => {
    ModalInjector.modalService.open({
      templateUrl: ReactInjector.overrideRegistry.getTemplate(
        'cf.resize.modal',
        require('./resize/resizeServerGroup.html'),
      ),
      size: 'lg',
      controller: 'cloudfoundryResizeServerGroupCtrl as ctrl',
      resolve: {
        serverGroup: () => this.props.serverGroup,
        application: () => this.props.app,
      },
    });
  };

  public render(): JSX.Element {
    const { app, serverGroup } = this.props;

    const { AddEntityTagLinks } = NgReact;
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
          {this.hasDisabledInstances() &&
            !this.isEnableLocked() && (
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
