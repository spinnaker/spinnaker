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

import { IAmazonServerGroup, IAmazonServerGroupView } from 'amazon/domain';
import { AmazonCloneServerGroupModal } from 'amazon/serverGroup/configure/wizard/AmazonCloneServerGroupModal';
import { AwsReactInjector } from 'amazon/reactShims';
import { IAmazonServerGroupCommand } from '../configure';

export interface IAmazonServerGroupActionsProps extends IServerGroupActionsProps {
  serverGroup: IAmazonServerGroupView;
}

export class AmazonServerGroupActions extends React.Component<IAmazonServerGroupActionsProps> {
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
      provider: 'aws',
      taskMonitorConfig: taskMonitor,
      interestingHealthProviderNames: undefined as string[],
      submitMethod,
      askForReason: true,
      platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
      platformHealthType: 'Amazon',
      onTaskComplete: () => {
        if (ReactInjector.$state.includes('**.serverGroup', stateParams)) {
          ReactInjector.$state.go('^');
        }
      },
    };

    ServerGroupWarningMessageService.addDestroyWarningMessage(app, serverGroup, confirmationModalParams);

    if (app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
      confirmationModalParams.interestingHealthProviderNames = ['Amazon'];
    }

    ReactInjector.confirmationModalService.confirm(confirmationModalParams);
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
      provider: 'aws',
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
      buttonText: `Yes, take me to the rollback settings modal`,
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

    ReactInjector.confirmationModalService.confirm(confirmationModalParams);
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

  private resizeServerGroup = (): void => {
    ModalInjector.modalService.open({
      templateUrl: ReactInjector.overrideRegistry.getTemplate(
        'aws.resize.modal',
        require('./resize/resizeServerGroup.html'),
      ),
      controller: 'awsResizeServerGroupCtrl as ctrl',
      resolve: {
        serverGroup: () => this.props.serverGroup,
        application: () => this.props.app,
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
