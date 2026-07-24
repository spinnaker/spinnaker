import React from 'react';
import { Dropdown } from 'react-bootstrap';

import type { Application, IServerGroup, IServerGroupActionsProps, ITask } from '@spinnaker/core';
import { ConfirmationModalService, ReactInjector, TaskExecutor } from '@spinnaker/core';

import { ProxmoxResizeServerGroupModal } from './ProxmoxResizeServerGroupModal';

const submitLifecycleTask = (
  type: 'startProxmoxServerGroup' | 'stopProxmoxServerGroup',
  serverGroup: IServerGroup,
  application: Application,
  description: string,
): PromiseLike<ITask> =>
  TaskExecutor.executeTask({
    job: [
      {
        type,
        serverGroupName: serverGroup.name,
        asgName: serverGroup.name,
        region: serverGroup.region,
        credentials: serverGroup.account,
        cloudProvider: 'proxmox',
      },
    ],
    application,
    description: `${description}: ${serverGroup.name}`,
  });

export function ProxmoxServerGroupActions({ app, serverGroup }: IServerGroupActionsProps): JSX.Element {
  const resize = () => ProxmoxResizeServerGroupModal.show({ application: app, serverGroup });

  const stop = () =>
    ConfirmationModalService.confirm({
      header: `Really stop ${serverGroup.name}?`,
      buttonText: `Stop ${serverGroup.name}`,
      account: serverGroup.account,
      body: '<p>Stopping shuts down every VM in this server group. The VMs are not deleted.</p>',
      taskMonitorConfig: { application: app, title: `Stopping ${serverGroup.name}` },
      submitMethod: () => submitLifecycleTask('stopProxmoxServerGroup', serverGroup, app, 'Stop Server Group'),
      askForReason: true,
    });

  const start = () =>
    ConfirmationModalService.confirm({
      header: `Really start ${serverGroup.name}?`,
      buttonText: `Start ${serverGroup.name}`,
      account: serverGroup.account,
      taskMonitorConfig: { application: app, title: `Starting ${serverGroup.name}` },
      submitMethod: () => submitLifecycleTask('startProxmoxServerGroup', serverGroup, app, 'Start Server Group'),
      askForReason: true,
    });

  const destroy = () => {
    const stateParams = {
      name: serverGroup.name,
      accountId: serverGroup.account,
      region: serverGroup.region,
    };

    ConfirmationModalService.confirm({
      header: `Really destroy ${serverGroup.name}?`,
      buttonText: `Destroy ${serverGroup.name}`,
      account: serverGroup.account,
      body: '<p>Destroying stops and permanently deletes every VM in this server group.</p>',
      taskMonitorConfig: {
        application: app,
        title: `Destroying ${serverGroup.name}`,
        onTaskComplete: () => {
          if (ReactInjector.$state.includes('**.serverGroup', stateParams)) {
            ReactInjector.$state.go('^');
          }
        },
      },
      submitMethod: (params: any = {}) => {
        params.serverGroupName = serverGroup.name;
        return ReactInjector.serverGroupWriter.destroyServerGroup(serverGroup, app, params);
      },
      askForReason: true,
    });
  };

  return (
    <Dropdown className="dropdown" id="server-group-actions-dropdown">
      <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">Server Group Actions</Dropdown.Toggle>
      <Dropdown.Menu className="dropdown-menu">
        <li>
          <a className="clickable" onClick={resize}>
            Resize
          </a>
        </li>
        <li>
          <a className="clickable" onClick={start}>
            Start
          </a>
        </li>
        <li>
          <a className="clickable" onClick={stop}>
            Stop
          </a>
        </li>
        <li role="presentation" className="divider" />
        <li>
          <a className="clickable" onClick={destroy}>
            Destroy
          </a>
        </li>
      </Dropdown.Menu>
    </Dropdown>
  );
}
