import * as React from 'react';
import * as classNames from 'classnames';
import { orderBy } from 'lodash';

import {
  Application,
  IClusterSubgroup,
  IInstanceCounts,
  IServerGroup,
  IServerGroupManagerSubgroup,
  ISortFilter,
  ReactInjector,
  ServerGroup,
} from 'core';
import { ServerGroupManagerHeading } from './ServerGroupManagerHeading';

interface IServerGroupManagerProps {
  manager: IServerGroupManagerSubgroup;
  grouping: IClusterSubgroup;
  application: Application;
  sortFilter: ISortFilter;
}

export class ServerGroupManager extends React.Component<IServerGroupManagerProps> {
  private isSelected = (): boolean => {
    const { manager } = this.props;
    const [serverGroup] = manager.serverGroups;
    const params = {
      accountId: serverGroup.account,
      region: serverGroup.region,
      provider: serverGroup.cloudProvider,
      serverGroupManager: manager.heading,
    };
    return ReactInjector.$state.includes('**.serverGroupManager', params);
  };

  private handleClick = (e: React.MouseEvent<HTMLElement>): void => {
    const { manager } = this.props;
    const [serverGroup] = manager.serverGroups;
    const nextState = ReactInjector.$state.current.name.endsWith('.clusters')
      ? '.serverGroupManager'
      : '^.serverGroupManager';

    e.preventDefault();
    e.stopPropagation();
    ReactInjector.$state.go(nextState, {
      accountId: serverGroup.account,
      region: serverGroup.region,
      provider: serverGroup.cloudProvider,
      serverGroupManager: manager.heading,
    });
  };

  private buildHealthCounts = (): IInstanceCounts => {
    const {
      manager: { serverGroups },
    } = this.props;
    const pick = (key: keyof IInstanceCounts) => (total: number, serverGroup: IServerGroup): number =>
      total + serverGroup.instanceCounts[key];

    return {
      up: serverGroups.reduce(pick('up'), 0),
      down: serverGroups.reduce(pick('down'), 0),
      starting: serverGroups.reduce(pick('starting'), 0),
      succeeded: serverGroups.reduce(pick('succeeded'), 0),
      failed: serverGroups.reduce(pick('failed'), 0),
      unknown: serverGroups.reduce(pick('unknown'), 0),
      outOfService: serverGroups.reduce(pick('outOfService'), 0),
    };
  };

  public render() {
    const { manager, application, sortFilter, grouping } = this.props;
    const [serverGroup] = manager.serverGroups;
    const classes = {
      active: this.isSelected(),
      clickable: true,
      'clickable-row': true,
      'rollup-details': true,
    };
    const sortedServerGroups = orderBy(
      manager.serverGroups,
      [manager.serverGroups.every(sg => !!sg.moniker) ? 'moniker.sequence' : 'name'],
      ['desc'],
    );

    return (
      <div className={classNames(classes)}>
        <ServerGroupManagerHeading
          onClick={this.handleClick}
          health={this.buildHealthCounts()}
          provider={serverGroup.type}
        />

        {sortedServerGroups.map((sg: IServerGroup) => (
          <ServerGroup
            key={sg.name}
            serverGroup={sg}
            cluster={sg.cluster}
            application={application}
            sortFilter={sortFilter}
            hasDiscovery={grouping.hasDiscovery}
            hasLoadBalancers={grouping.hasLoadBalancers}
            hasManager={true}
          />
        ))}
      </div>
    );
  }
}
