import React from 'react';

import { Application } from '../application';
import { IServerGroup, IServerGroupManager } from '../domain';
import { Tooltip } from '../presentation/Tooltip';
import { ReactInjector } from '../reactShims';

import { IServerGroupManagerStateParams } from './serverGroupManager.states';

export interface IServerGroupManagerTagProps {
  application: Application;
  serverGroup: IServerGroup;
}

export class ServerGroupManagerTag extends React.Component<IServerGroupManagerTagProps> {
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
    const { $state } = ReactInjector;
    const nextState = $state.current.name.endsWith('.clusters') ? '.serverGroupManager' : '^.serverGroupManager';
    $state.go(nextState, this.buildStateParams());
  };

  private buildStateParams(): IServerGroupManagerStateParams {
    const serverGroupManager = this.extractServerGroupManager();
    return {
      accountId: serverGroupManager.account,
      region: serverGroupManager.region,
      provider: serverGroupManager.cloudProvider,
      serverGroupManager: serverGroupManager.name,
    };
  }
}
