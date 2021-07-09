import classNames from 'classnames';
import React from 'react';

import { ServerGroupManagerHeading } from './ServerGroupManagerHeading';
import { Application } from '../application';
import { IClusterSubgroup } from '../cluster';
import { IInstanceCounts, IServerGroup } from '../domain';
import { ISortFilter } from '../filterModel';
import { ReactInjector } from '../reactShims';
import { ServerGroup } from '../serverGroup';

interface IServerGroupManagerProps {
  grouping: IClusterSubgroup;
  manager: string;
  application: Application;
  sortFilter: ISortFilter;
  serverGroups: IServerGroup[];
}

export class ServerGroupManager extends React.Component<IServerGroupManagerProps> {
  private isSelected = (): boolean => {
    const { manager, serverGroups } = this.props;
    const params = {
      accountId: serverGroups[0].account,
      region: serverGroups[0].region,
      provider: serverGroups[0].cloudProvider,
      serverGroupManager: manager,
    };
    return ReactInjector.$state.includes('**.serverGroupManager', params);
  };

  private handleClick = (e: React.MouseEvent<HTMLElement>): void => {
    const { manager, serverGroups } = this.props;
    const nextState = ReactInjector.$state.current.name.endsWith('.clusters')
      ? '.serverGroupManager'
      : '^.serverGroupManager';

    e.preventDefault();
    e.stopPropagation();
    ReactInjector.$state.go(nextState, {
      accountId: serverGroups[0].account,
      region: serverGroups[0].region,
      provider: serverGroups[0].cloudProvider,
      serverGroupManager: manager,
    });
  };

  private buildHealthCounts = (): IInstanceCounts => {
    const { serverGroups } = this.props;
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
    const { application, sortFilter, grouping, serverGroups, manager } = this.props;
    const classes = {
      active: this.isSelected(),
      clickable: true,
      'clickable-row': true,
      'rollup-details': true,
    };

    return (
      <div className={classNames(classes)}>
        <ServerGroupManagerHeading
          onClick={this.handleClick}
          health={this.buildHealthCounts()}
          provider={serverGroups[0].type}
          heading={manager}
          grouping={grouping}
          app={application}
        />

        {serverGroups.map((sg: IServerGroup) => (
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
