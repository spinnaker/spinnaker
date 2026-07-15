import React from 'react';

import type { ILoadBalancerActionsProps, ILoadBalancerDeleteCommand } from '@spinnaker/core';
import { ConfirmationModalService, LoadBalancerWriter } from '@spinnaker/core';

import { AppengineCreateLoadBalancerModal } from '../configure/AppengineCreateLoadBalancerModal';

export function canDeleteAppengineLoadBalancer(loadBalancer: any): boolean {
  return loadBalancer.name !== 'default';
}

export function getDeleteLoadBalancerWarning(loadBalancer: any): string {
  const serverGroupNames = (loadBalancer.serverGroups || []).map((serverGroup: any) => serverGroup.name);
  if (!serverGroupNames.length) {
    return null;
  }

  if (serverGroupNames.length === 1) {
    return `<div class="alert alert-warning"><p>Deleting <b>${loadBalancer.name}</b> will destroy <b>${serverGroupNames[0]}</b>.</p></div>`;
  }

  return `<div class="alert alert-warning"><p>Deleting <b>${
    loadBalancer.name
  }</b> will destroy the following server groups:<ul>${serverGroupNames
    .map((name: string) => `<li>${name}</li>`)
    .join('')}</ul></p></div>`;
}

export function AppengineLoadBalancerActions({ app, loadBalancer }: ILoadBalancerActionsProps) {
  const deleteLoadBalancer = () => {
    const command: ILoadBalancerDeleteCommand = {
      cloudProvider: loadBalancer.cloudProvider,
      loadBalancerName: loadBalancer.name,
      credentials: loadBalancer.account,
    };

    ConfirmationModalService.confirm({
      header: `Really delete ${loadBalancer.name}?`,
      buttonText: `Delete ${loadBalancer.name}`,
      body: getDeleteLoadBalancerWarning(loadBalancer),
      account: loadBalancer.account,
      taskMonitorConfig: { application: app, title: `Deleting ${loadBalancer.name}` },
      submitMethod: () => LoadBalancerWriter.deleteLoadBalancer(command, app),
    });
  };

  return (
    <div className="actions">
      <button
        className="btn btn-sm btn-default"
        onClick={() => AppengineCreateLoadBalancerModal.show({ app, loadBalancer, isNew: false })}
        type="button"
      >
        Edit
      </button>
      <button
        className="btn btn-sm btn-default"
        disabled={!canDeleteAppengineLoadBalancer(loadBalancer)}
        onClick={deleteLoadBalancer}
        type="button"
      >
        Delete
      </button>
    </div>
  );
}
