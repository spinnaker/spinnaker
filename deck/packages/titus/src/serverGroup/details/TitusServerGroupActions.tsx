import { orderBy } from 'lodash';
import React from 'react';
import { Dropdown } from 'react-bootstrap';

import type { IRouterInjectedProps, IServerGroupActionsProps } from '@spinnaker/core';
import {
  AddEntityTagLinks,
  ConfirmationModalService,
  confirmNotManaged,
  DeckRuntimeContext,
  ReactModal,
  ServerGroupWarningMessageService,
  SETTINGS,
  withRouter,
} from '@spinnaker/core';

import { TitusServerGroupCommandBuilder } from '../configure/ServerGroupCommandBuilder';
import { TitusCloneServerGroupModal } from '../configure/wizard/TitusCloneServerGroupModal';
import { TitusResizeServerGroupModal } from './resize/TitusResizeServerGroupModal';
import { TitusRollbackServerGroupModal } from './rollback/TitusRollbackServerGroupModal';

export class TitusServerGroupActionsComponent extends React.Component<IServerGroupActionsProps & IRouterInjectedProps> {
  public static contextType = DeckRuntimeContext;
  public declare context: React.ContextType<typeof DeckRuntimeContext>;

  private destroyServerGroup = (): void => {
    const { app, serverGroup } = this.props;
    const taskMonitorConfig = {
      application: app,
      title: 'Destroying ' + serverGroup.name,
      onTaskComplete: () => {
        const stateParams = { name: serverGroup.name, accountId: serverGroup.account, region: serverGroup.region };
        if (this.props.stateService.includes('**.serverGroup', stateParams)) {
          this.props.stateService.go('^');
        }
      },
    };
    const confirmationModalParams = {
      header: 'Really destroy ' + serverGroup.name + '?',
      buttonText: 'Destroy ' + serverGroup.name,
      account: serverGroup.account,
      taskMonitorConfig,
      platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
      platformHealthType: 'Titus',
      submitMethod: () =>
        this.context.services.serverGroupWriter.destroyServerGroup(serverGroup, app, {
          cloudProvider: 'titus',
          serverGroupName: serverGroup.name,
          region: serverGroup.region,
        }),
    };
    ServerGroupWarningMessageService.addDestroyWarningMessage(app, serverGroup, confirmationModalParams);
    confirmNotManaged(serverGroup, app).then(
      (notManaged) => notManaged && ConfirmationModalService.confirm(confirmationModalParams),
    );
  };

  private disableServerGroup = (): void => {
    const { app, serverGroup } = this.props;
    const confirmationModalParams = {
      header: 'Really disable ' + serverGroup.name + '?',
      buttonText: 'Disable ' + serverGroup.name,
      account: serverGroup.account,
      taskMonitorConfig: { application: app, title: 'Disabling ' + serverGroup.name },
      platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
      platformHealthType: 'Titus',
      submitMethod: () =>
        this.context.services.serverGroupWriter.disableServerGroup(serverGroup, app.name, {
          cloudProvider: 'titus',
          serverGroupName: serverGroup.name,
          region: serverGroup.region,
          zone: serverGroup.zones[0],
        }),
    };
    ServerGroupWarningMessageService.addDisableWarningMessage(app, serverGroup, confirmationModalParams);
    confirmNotManaged(serverGroup, app).then(
      (notManaged) => notManaged && ConfirmationModalService.confirm(confirmationModalParams),
    );
  };

  private showEnableServerGroupModal = (): void => {
    const { app, serverGroup } = this.props;
    ConfirmationModalService.confirm({
      header: 'Really enable ' + serverGroup.name + '?',
      buttonText: 'Enable ' + serverGroup.name,
      account: serverGroup.account,
      taskMonitorConfig: { application: app, title: 'Enabling ' + serverGroup.name },
      platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
      platformHealthType: 'Titus',
      submitMethod: () =>
        this.context.services.serverGroupWriter.enableServerGroup(serverGroup, app, {
          cloudProvider: 'titus',
          serverGroupName: serverGroup.name,
          region: serverGroup.region,
          zone: serverGroup.zones[0],
        }),
    });
  };

  private enableServerGroup = (): void => {
    const { app, serverGroup } = this.props;
    confirmNotManaged(serverGroup, app).then((notManaged) => {
      if (!notManaged) {
        return;
      }

      if (!this.isRollbackEnabled()) {
        this.showEnableServerGroupModal();
        return;
      }

      ConfirmationModalService.confirm({
        header: 'Rolling back?',
        body: `Spinnaker provides an orchestrated rollback feature to carefully restore a different version of this
          server group. Do you want to use the orchestrated rollback?`,
        buttonText: 'Yes, take me to the rollback settings modal',
        cancelButtonText: 'No, I just want to enable the server group',
      })
        .then(() => new Promise((resolve) => setTimeout(resolve, 500)))
        .then(() => this.rollbackServerGroup())
        .catch(({ source }) => {
          if (source === 'footer') {
            this.showEnableServerGroupModal();
          }
        });
    });
  };

  private isRollbackEnabled = (): boolean => {
    const { app, serverGroup } = this.props;
    if (!serverGroup.isDisabled) {
      return true;
    }

    return app
      .getDataSource('serverGroups')
      .data.some(
        (g: any) =>
          g.cluster === serverGroup.cluster &&
          g.region === serverGroup.region &&
          g.account === serverGroup.account &&
          g.isDisabled === false,
      );
  };

  private rollbackServerGroup = (): void => {
    const { app } = this.props;
    let serverGroup: any = this.props.serverGroup;
    let previousServerGroup: any;
    let allServerGroups = app
      .getDataSource('serverGroups')
      .data.filter(
        (g: any) =>
          g.cluster === serverGroup.cluster && g.region === serverGroup.region && g.account === serverGroup.account,
      );

    if (serverGroup.isDisabled) {
      previousServerGroup = serverGroup;
      serverGroup = orderBy(
        allServerGroups.filter((g: any) => g.name !== previousServerGroup.name && !g.isDisabled),
        ['instanceCounts.total', 'createdTime'],
        ['desc', 'desc'],
      )[0];
      if (!serverGroup) {
        return;
      }
    }

    allServerGroups = allServerGroups.filter((g: any) => g.name !== serverGroup.name);
    if (allServerGroups.length === 1 && !previousServerGroup) {
      previousServerGroup = allServerGroups[0];
    }

    confirmNotManaged(serverGroup, app).then((notManaged) => {
      if (!notManaged) {
        return;
      }
      TitusRollbackServerGroupModal.show(
        {
          allServerGroups,
          application: app,
          previousServerGroup,
          serverGroup,
        } as any,
        this.context.services,
      );
    });
  };

  private resizeServerGroup = (): void => {
    const { app, serverGroup } = this.props;
    confirmNotManaged(serverGroup, app).then(
      (notManaged) =>
        notManaged &&
        ReactModal.show(
          TitusResizeServerGroupModal,
          { serverGroup, application: app } as any,
          undefined,
          this.context.services,
        ),
    );
  };

  private cloneServerGroup = (): void => {
    const { app, serverGroup } = this.props;
    TitusServerGroupCommandBuilder.buildServerGroupCommandFromExisting(app, serverGroup).then((command: any) => {
      TitusCloneServerGroupModal.show(
        { title: `Clone ${serverGroup.name}`, application: app, command },
        this.context.services,
      );
    });
  };

  public render(): JSX.Element {
    const { app, serverGroup } = this.props;
    const showEntityTags = SETTINGS.feature && SETTINGS.feature.entityTags;
    return (
      <Dropdown className="dropdown" id="server-group-actions-dropdown">
        <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">Server Group Actions</Dropdown.Toggle>
        <Dropdown.Menu className="dropdown-menu">
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
          {serverGroup.isDisabled && (
            <li>
              <a className="clickable" onClick={this.enableServerGroup}>
                Enable
              </a>
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
          {this.isRollbackEnabled() && (
            <>
              <li>
                <a className="clickable" onClick={this.rollbackServerGroup}>
                  Rollback
                </a>
              </li>
              <li className="divider" role="presentation" />
            </>
          )}
          {showEntityTags && (
            <AddEntityTagLinks
              component={serverGroup}
              application={app}
              entityType="serverGroup"
              ownerOptions={(serverGroup as any).entityTagTargets || []}
              onUpdate={() => app.serverGroups.refresh()}
            />
          )}
        </Dropdown.Menu>
      </Dropdown>
    );
  }
}

export const TitusServerGroupActions = withRouter(TitusServerGroupActionsComponent);
