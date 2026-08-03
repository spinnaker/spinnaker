import React from 'react';

import type { Application } from '../application';
import type { IServerGroup, IServerGroupManager } from '../domain';
import type { IRouterInjectedProps } from '../navigation/routerContext';
import { withRouter } from '../navigation/routerContext';
import { Tooltip } from '../presentation/Tooltip';

import type { IServerGroupManagerStateParams } from './serverGroupManager.states';

export interface IServerGroupManagerTagProps {
  application: Application;
  serverGroup: IServerGroup;
}

class ServerGroupManagerTagComponent extends React.Component<IServerGroupManagerTagProps & IRouterInjectedProps> {
  public render() {
    const serverGroupManager = this.extractServerGroupManager();
    if (!serverGroupManager) {
      return null;
    }

    return (
      <span className="server-group-manager-tag">
        <Tooltip value={`Server Group Manager: ${serverGroupManager.name}`}>
          <button className="btn btn-link no-padding btn-server-group-manager" onClick={this.openDetails}>
            <span className="badge badge-counter">
              <span className="fa fa-server" />
            </span>
          </button>
        </Tooltip>
      </span>
    );
  }

  private extractServerGroupManager(): IServerGroupManager {
    const { application, serverGroup } = this.props;
    return application
      .getDataSource('serverGroupManagers')
      .data.find((manager: IServerGroupManager) =>
        manager.serverGroups.some(
          (group) =>
            serverGroup.name === group.name &&
            serverGroup.region === manager.region &&
            serverGroup.account === manager.account,
        ),
      );
  }

  private openDetails = (event: React.MouseEvent<HTMLElement>): void => {
    event.preventDefault();
    const { stateService } = this.props;
    const nextState = stateService.current.name.endsWith('.clusters') ? '.serverGroupManager' : '^.serverGroupManager';
    stateService.go(nextState, this.buildStateParams());
  };

  private buildStateParams(): IServerGroupManagerStateParams {
    const serverGroupManager = this.extractServerGroupManager();
    return {
      accountId: serverGroupManager.account,
      region: serverGroupManager.region,
      provider: serverGroupManager.cloudProvider,
      name: serverGroupManager.name,
    };
  }
}

export const ServerGroupManagerTag = withRouter(ServerGroupManagerTagComponent);
ServerGroupManagerTag.displayName = 'ServerGroupManagerTag';
