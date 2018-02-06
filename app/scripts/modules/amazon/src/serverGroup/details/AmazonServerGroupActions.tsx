import * as React from 'react';
import { BindAll } from 'lodash-decorators';
import { Dropdown, Tooltip } from 'react-bootstrap';
import { get, find, filter } from 'lodash';

import { IOwnerOption, IServerGroupActionsProps, IServerGroupJob, NgReact, ReactInjector, SETTINGS } from '@spinnaker/core';

import { IAmazonServerGroup, IAmazonServerGroupView } from 'amazon/domain';
import { AwsReactInjector } from 'amazon/reactShims';

export interface IAmazonServerGroupActionsProps extends IServerGroupActionsProps {
  serverGroup: IAmazonServerGroupView;
}

@BindAll()
export class AmazonServerGroupActions extends React.Component<IAmazonServerGroupActionsProps> {
  private isEnableLocked(): boolean {
    if (this.props.serverGroup.isDisabled) {
      const resizeTasks = (this.props.serverGroup.runningTasks || [])
        .filter(task => get(task, 'execution.stages', []).some(
          stage => stage.type === 'resizeServerGroup'));
      if (resizeTasks.length) {
        return true;
      }
    }
    return false;
  }

  private hasDisabledInstances(): boolean {
    // server group may have disabled instances (out of service) but NOT itself be disabled
    return this.props.serverGroup.isDisabled || (get(this.props.serverGroup, 'instanceCounts.outOfService', 0) > 0);
  }

  private destroyServerGroup(): void {
    const { app, serverGroup } = this.props;

    const taskMonitor = {
      application: app,
      title: 'Destroying ' + serverGroup.name,
    };

    const submitMethod = (params: IServerGroupJob) => ReactInjector.serverGroupWriter.destroyServerGroup(serverGroup, app, params);

    const stateParams = {
      name: serverGroup.name,
      accountId: serverGroup.account,
      region: serverGroup.region
    };

    const confirmationModalParams = {
      header: 'Really destroy ' + serverGroup.name + '?',
      buttonText: 'Destroy ' + serverGroup.name,
      account: serverGroup.account,
      provider: 'aws',
      taskMonitorConfig: taskMonitor,
      interestingHealthProviderNames: undefined as string[],
      submitMethod: submitMethod,
      askForReason: true,
      platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
      platformHealthType: 'Amazon',
      onTaskComplete: () => {
        if (ReactInjector.$state.includes('**.serverGroup', stateParams)) {
          ReactInjector.$state.go('^');
        }
      }
    };

    ReactInjector.serverGroupWarningMessageService.addDestroyWarningMessage(app, serverGroup, confirmationModalParams);

    if (app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
      confirmationModalParams.interestingHealthProviderNames = ['Amazon'];
    }

    ReactInjector.confirmationModalService.confirm(confirmationModalParams);
  }

  private disableServerGroup(): void {
    const { app, serverGroup } = this.props;

    const taskMonitor = {
      application: app,
      title: 'Disabling ' + serverGroup.name
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
      submitMethod: submitMethod,
      askForReason: true
    };

    ReactInjector.serverGroupWarningMessageService.addDisableWarningMessage(app, serverGroup, confirmationModalParams);

    if (app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
      confirmationModalParams.interestingHealthProviderNames = ['Amazon'];
    }

    ReactInjector.confirmationModalService.confirm(confirmationModalParams);
  }

  private enableServerGroup(): void {
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
      submitMethod: submitMethod,
      askForReason: true
    };

    if (app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
      confirmationModalParams.interestingHealthProviderNames = ['Amazon'];
    }

    ReactInjector.confirmationModalService.confirm(confirmationModalParams);
  };

  private rollbackServerGroup(): void {
    const { app, serverGroup } = this.props;

    ReactInjector.modalService.open({
      templateUrl: ReactInjector.overrideRegistry.getTemplate('aws.rollback.modal', require('./rollback/rollbackServerGroup.html')),
      controller: 'awsRollbackServerGroupCtrl as ctrl',
      resolve: {
        serverGroup: () => serverGroup,
        disabledServerGroups: () => {
          const cluster = find(app.clusters, { name: serverGroup.cluster, account: serverGroup.account, serverGroups: [] });
          return filter(cluster.serverGroups, { isDisabled: true, region: serverGroup.region });
        },
        allServerGroups: () => app.getDataSource('serverGroups').data.filter((g: IAmazonServerGroup) =>
          g.cluster === serverGroup.cluster &&
          g.region === serverGroup.region &&
          g.account === serverGroup.account &&
          g.name !== serverGroup.name
        ),
        application: () => app
      }
    });
  };

  private resizeServerGroup(): void {
    ReactInjector.modalService.open({
      templateUrl: ReactInjector.overrideRegistry.getTemplate('aws.resize.modal', require('./resize/resizeServerGroup.html')),
      controller: 'awsResizeServerGroupCtrl as ctrl',
      resolve: {
        serverGroup: () => this.props.serverGroup,
        application: () => this.props.app
      }
    });
  }

  private cloneServerGroup(): void {
    const { app, serverGroup } = this.props;
    ReactInjector.modalService.open({
      templateUrl: require('../configure/wizard/serverGroupWizard.html'),
      controller: 'awsCloneServerGroupCtrl as ctrl',
      size: 'lg',
      resolve: {
        title: () => 'Clone ' + serverGroup.name,
        application: () => app,
        serverGroupCommand: () => AwsReactInjector.awsServerGroupCommandBuilder.buildServerGroupCommandFromExisting(app, serverGroup),
      }
    });
  }

  public render(): JSX.Element {
    const { app, serverGroup } = this.props;

    const { AddEntityTagLinks } = NgReact;
    const showEntityTags = SETTINGS.feature && SETTINGS.feature.entityTags;
    const entityTagTargets: IOwnerOption[] = ReactInjector.clusterTargetBuilder.buildClusterTargets(serverGroup);

    return (
      <Dropdown className="dropdown" id="server-group-actions-dropdown">
        <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">
          Server Group Actions
        </Dropdown.Toggle>
        <Dropdown.Menu className="dropdown-menu">
          {!serverGroup.isDisabled && <li><a className="clickable" onClick={this.rollbackServerGroup}>Rollback</a></li>}
          {!serverGroup.isDisabled && <li role="presentation" className="divider"/>}
          <li><a className="clickable" onClick={this.resizeServerGroup}>Resize</a></li>
          {!serverGroup.isDisabled && <li><a className="clickable" onClick={this.disableServerGroup}>Disable</a></li>}
          {this.hasDisabledInstances() && !this.isEnableLocked() && <li><a className="clickable" onClick={this.enableServerGroup}>Enable</a></li>}
          {this.isEnableLocked() && (
            <li className="disabled">
              <Tooltip value="Cannot enable this server group until resize operation completes" placement="left">
                <a><span className="small glyphicon glyphicon-lock"/>{' '}Enable</a>
              </Tooltip>
            </li>
          )}
          <li><a className="clickable" onClick={this.destroyServerGroup}>Destroy</a></li>
          <li><a className="clickable" onClick={this.cloneServerGroup}>Clone</a></li>
          {showEntityTags && (
            <AddEntityTagLinks
              component={serverGroup}
              application={app}
              entityType="serverGroup"
              ownerOptions={entityTagTargets}
              onUpdate={app.serverGroups.refresh}
            />
          )}
        </Dropdown.Menu>
      </Dropdown>
    );
  }
}
