import * as React from 'react';
import { BindAll } from 'lodash-decorators';

import { Application } from 'core/application';
import { IServerGroupManager, IServerGroup } from 'core/domain';
import { ReactInjector } from 'core/reactShims';
import { Tooltip } from 'core/presentation/Tooltip';
import { IServerGroupManagerStateParams } from 'core/serverGroupManager';

export interface IServerGroupManagerTagProps {
  application: Application;
  serverGroup: IServerGroup;
}

@BindAll()
export class ServerGroupManagerTag extends React.Component<IServerGroupManagerTagProps> {
  public render() {
    const serverGroupManager = this.extractServerGroupManager();
    if (!serverGroupManager) {
      return null;
    }

    return (
      <Tooltip value={`Server Group Manager: ${serverGroupManager.name}`}>
        <span className="server-group-manager-tag">
          <span className="badge badge-counter">
            <span onClick={this.openDetails} className="fa fa-server" />
          </span>
        </span>
      </Tooltip>
    );
  }

  private extractServerGroupManager(): IServerGroupManager {
    const { application, serverGroup } = this.props;
    return application
      .getDataSource('serverGroupManagers')
      .data.find((manager: IServerGroupManager) =>
        manager.serverGroups.some(
          group =>
            serverGroup.name === group.name &&
            serverGroup.region === manager.region &&
            serverGroup.account === manager.account,
        ),
      );
  }

  private openDetails(event: React.MouseEvent<HTMLElement>): void {
    event.preventDefault();
    const { $state } = ReactInjector;
    const nextState = $state.current.name.endsWith('.clusters') ? '.serverGroupManager' : '^.serverGroupManager';
    $state.go(nextState, this.buildStateParams());
  }

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
