import React from 'react';

import type { Application, IConfirmationModalParams, IServerGroup, IServerGroupActionsProps } from '@spinnaker/core';
import { ConfirmationModalService, ServerGroupWarningMessageService } from '@spinnaker/core';

import { AppengineHealth } from '../../common/appengineHealth';
import { AppengineServerGroupCommandBuilder } from '../configure/serverGroupCommandBuilder.service';
import { AppengineCloneServerGroupModal } from '../configure/wizard/AppengineCloneServerGroupModal';
import type { IAppengineServerGroup } from '../../domain';
import { AppengineServerGroupWriter } from '../writer/serverGroup.write.service';

const writer = new AppengineServerGroupWriter();
const commandBuilder = new AppengineServerGroupCommandBuilder();

export function canStartOrStopServerGroup(serverGroup: IAppengineServerGroup): boolean {
  return serverGroup.env === 'FLEXIBLE' || ['MANUAL', 'BASIC'].includes(serverGroup.scalingPolicy?.type);
}

export function canStartServerGroup(serverGroup: IAppengineServerGroup): boolean {
  return canStartOrStopServerGroup(serverGroup) && serverGroup.servingStatus === 'STOPPED';
}

export function canStopServerGroup(serverGroup: IAppengineServerGroup): boolean {
  return canStartOrStopServerGroup(serverGroup) && serverGroup.servingStatus === 'SERVING';
}

export function expectedAllocationsAfterDisableOperation(
  serverGroup: IServerGroup,
  loadBalancers: any[] = [],
): { [key: string]: number } {
  const loadBalancer = loadBalancers.find((toCheck) => {
    return Object.keys(toCheck.split?.allocations || {}).includes(serverGroup.name);
  });

  if (!loadBalancer) {
    return null;
  }

  const allocations: { [key: string]: number } = { ...loadBalancer.split.allocations };
  delete allocations[serverGroup.name];
  const denominator = Object.values(allocations).reduce((sum, allocation) => sum + allocation, 0);
  if (!denominator) {
    return {};
  }

  const precision = loadBalancer.split.shardBy === 'COOKIE' ? 1000 : 100;
  Object.keys(allocations).forEach((serverGroupName) => {
    allocations[serverGroupName] = Math.round((allocations[serverGroupName] / denominator) * precision) / precision;
  });
  return allocations;
}

export function canDisableServerGroup(serverGroup: IAppengineServerGroup, loadBalancers: any[] = []): boolean {
  if (!serverGroup || serverGroup.disabled) {
    return false;
  }

  return Object.keys(expectedAllocationsAfterDisableOperation(serverGroup, loadBalancers) || {}).length > 0;
}

export function canDestroyServerGroup(serverGroup: IAppengineServerGroup, loadBalancers: any[] = []): boolean {
  if (!serverGroup) {
    return false;
  }

  return (
    serverGroup.disabled ||
    Object.keys(expectedAllocationsAfterDisableOperation(serverGroup, loadBalancers) || {}).length > 0
  );
}

function buildExpectedAllocationsTable(expectedAllocations: { [key: string]: number }): string {
  const rows = Object.keys(expectedAllocations || {})
    .map(
      (serverGroupName) =>
        `<tr><td>${serverGroupName}</td><td>${expectedAllocations[serverGroupName] * 100}%</td></tr>`,
    )
    .join('');

  return `<table class="table table-condensed"><thead><tr><th>Server Group</th><th>Allocation</th></tr></thead><tbody>${rows}</tbody></table>`;
}

function getLoadBalancers(app: Application): any[] {
  return app.getDataSource?.('loadBalancers')?.data || [];
}

function platformHealthParams(app: Application): Partial<IConfirmationModalParams> {
  const params: Partial<IConfirmationModalParams> = {
    platformHealthOnlyShowOverride: app.attributes?.platformHealthOnlyShowOverride,
    platformHealthType: AppengineHealth.PLATFORM,
    interestingHealthProviderNames: [],
  };
  if (app.attributes?.platformHealthOnlyShowOverride && app.attributes?.platformHealthOnly) {
    params.interestingHealthProviderNames = [AppengineHealth.PLATFORM];
  }
  return params;
}

function getDisableBody(serverGroup: IAppengineServerGroup, loadBalancers: any[]): string {
  const expectedAllocations = expectedAllocationsAfterDisableOperation(serverGroup, loadBalancers);
  return `<div class="well well-sm">
    <p>For App Engine, a disable operation sets this server group's allocation to 0% and redistributes traffic to the other enabled server groups.</p>
    <p>If you would like more fine-grained control, edit <b>${
      serverGroup.loadBalancers?.[0]
    }</b> under the <b>Load Balancers</b> tab.</p>
    <div class="row"><div class="col-md-12">${buildExpectedAllocationsTable(expectedAllocations)}</div></div>
  </div>`;
}

function getDestroyBody(app: Application, serverGroup: IAppengineServerGroup, loadBalancers: any[]): string {
  let body = '';
  const params: IConfirmationModalParams = {} as IConfirmationModalParams;
  ServerGroupWarningMessageService.addDestroyWarningMessage(app, serverGroup, params);
  if (params.body) {
    body += params.body;
  }
  if (!serverGroup.disabled) {
    body += `<div class="well well-sm">
      <p>A destroy operation will first disable this server group.</p>
      <p>For App Engine, a disable operation sets this server group's allocation to 0% and redistributes traffic to the other enabled server groups.</p>
      <p>If you would like more fine-grained control, edit <b>${
        serverGroup.loadBalancers?.[0]
      }</b> under the <b>Load Balancers</b> tab.</p>
      <div class="row"><div class="col-md-12">${buildExpectedAllocationsTable(
        expectedAllocationsAfterDisableOperation(serverGroup, loadBalancers),
      )}</div></div>
    </div>`;
  }
  return body;
}

export function AppengineServerGroupActions({ app, serverGroup }: IServerGroupActionsProps) {
  const appengineServerGroup = serverGroup as IAppengineServerGroup;
  const loadBalancers = getLoadBalancers(app);

  const clone = () => {
    commandBuilder.buildServerGroupCommandFromExisting(app, appengineServerGroup).then((command) => {
      AppengineCloneServerGroupModal.show({ application: app, command, title: `Clone ${serverGroup.name}` });
    });
  };

  const start = () =>
    ConfirmationModalService.confirm({
      header: `Really start ${serverGroup.name}?`,
      buttonText: `Start ${serverGroup.name}`,
      account: appengineServerGroup.account,
      taskMonitorConfig: { application: app, title: `Starting ${serverGroup.name}` },
      submitMethod: () => writer.startServerGroup(appengineServerGroup, app),
      askForReason: true,
      ...platformHealthParams(app),
    });

  const stop = () =>
    ConfirmationModalService.confirm({
      header: `Really stop ${serverGroup.name}?`,
      buttonText: `Stop ${serverGroup.name}`,
      account: appengineServerGroup.account,
      body: !appengineServerGroup.disabled
        ? `<div class="alert alert-danger"><p>Stopping this server group will scale it down to zero instances.</p><p>This server group is currently serving traffic from <b>${appengineServerGroup.loadBalancers?.[0]}</b>. Traffic directed to this server group after it has been stopped will not be handled.</p></div>`
        : undefined,
      taskMonitorConfig: { application: app, title: `Stopping ${serverGroup.name}` },
      submitMethod: () => writer.stopServerGroup(appengineServerGroup, app),
      askForReason: true,
      ...platformHealthParams(app),
    });

  const enable = () =>
    ConfirmationModalService.confirm({
      header: `Really enable ${serverGroup.name}?`,
      buttonText: `Enable ${serverGroup.name}`,
      account: appengineServerGroup.account,
      body: `<div class="well well-sm"><p>Enabling <b>${serverGroup.name}</b> will set its traffic allocation for <b>${appengineServerGroup.loadBalancers?.[0]}</b> to 100%.</p><p>If you would like more fine-grained control, edit <b>${appengineServerGroup.loadBalancers?.[0]}</b> under the <b>Load Balancers</b> tab.</p></div>`,
      taskMonitorConfig: { application: app, title: `Enabling ${serverGroup.name}` },
      submitMethod: (params: any) => writer.enableServerGroup(appengineServerGroup, app, params),
      askForReason: true,
      ...platformHealthParams(app),
    });

  const disable = () =>
    ConfirmationModalService.confirm({
      header: `Really disable ${serverGroup.name}?`,
      buttonText: `Disable ${serverGroup.name}`,
      account: appengineServerGroup.account,
      body: getDisableBody(appengineServerGroup, loadBalancers),
      taskMonitorConfig: { application: app, title: `Disabling ${serverGroup.name}` },
      submitMethod: (params: any) => writer.disableServerGroup(appengineServerGroup, app, params),
      askForReason: true,
      ...platformHealthParams(app),
    });

  const destroy = () =>
    ConfirmationModalService.confirm({
      header: `Really destroy ${serverGroup.name}?`,
      buttonText: `Destroy ${serverGroup.name}`,
      account: appengineServerGroup.account,
      body: getDestroyBody(app, appengineServerGroup, loadBalancers),
      taskMonitorConfig: { application: app, title: `Destroying ${serverGroup.name}` },
      submitMethod: (params: any) => writer.destroyServerGroup(appengineServerGroup, app, params),
      askForReason: true,
      ...platformHealthParams(app),
    });

  return (
    <div className="actions">
      <button className="btn btn-sm btn-default" onClick={clone} type="button">
        Clone
      </button>
      <button
        className="btn btn-sm btn-default"
        disabled={!canStartServerGroup(appengineServerGroup)}
        onClick={start}
        type="button"
      >
        Start
      </button>
      <button
        className="btn btn-sm btn-default"
        disabled={!canStopServerGroup(appengineServerGroup)}
        onClick={stop}
        type="button"
      >
        Stop
      </button>
      <button className="btn btn-sm btn-default" onClick={enable} type="button">
        Enable
      </button>
      <button
        className="btn btn-sm btn-default"
        disabled={!canDisableServerGroup(appengineServerGroup, loadBalancers)}
        onClick={disable}
        type="button"
      >
        Disable
      </button>
      <button
        className="btn btn-sm btn-default"
        disabled={!canDestroyServerGroup(appengineServerGroup, loadBalancers)}
        onClick={destroy}
        type="button"
      >
        Destroy
      </button>
    </div>
  );
}
