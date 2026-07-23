import classNames from 'classnames';
import React from 'react';

import { ServerGroupManagerHeading } from './ServerGroupManagerHeading';
import { AngularServices } from '../angular/services';
import type { Application } from '../application';
import type { IClusterSubgroup } from '../cluster';
import type { IInstanceCounts, IServerGroup } from '../domain';
import type { ISortFilter } from '../filterModel';
import { ServerGroup } from '../serverGroup';

interface IServerGroupManagerProps {
  grouping: IClusterSubgroup;
  manager: string;
  application: Application;
  sortFilter: ISortFilter;
  serverGroups: IServerGroup[];
}

export class ServerGroupManager extends React.Component<IServerGroupManagerProps> {
  private getDetailsHref(): string {
    const { application, manager, serverGroups } = this.props;
    const currentHash = window.location.hash || `#/applications/${application.name}`;
    const clustersPath = currentHash.includes('/clusters')
      ? currentHash.split('/clusters')[0]
      : `#/applications/${application.name}`;

    return `${clustersPath}/clusters/serverGroupManagerDetails/${serverGroups[0].cloudProvider}/${
      serverGroups[0].account
    }/${serverGroups[0].region}/${encodeURIComponent(manager)}`;
  }

  private isSelected = (): boolean => {
    const { manager, serverGroups } = this.props;
    const params = {
      accountId: serverGroups[0].account,
      region: serverGroups[0].region,
      provider: serverGroups[0].cloudProvider,
      serverGroupManager: manager,
      name: manager,
    };
    return AngularServices.$state.includes('**.serverGroupManager', params);
  };

  private handleClick = (e: React.MouseEvent<HTMLElement>): void => {
    e.stopPropagation();
    if (e.button === 0 && !e.metaKey && !e.ctrlKey && !e.shiftKey && !e.altKey) {
      e.preventDefault();
      window.location.hash = this.getDetailsHref();
    }
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
          detailsHref={this.getDetailsHref()}
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
