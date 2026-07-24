import React from 'react';
import { Dropdown } from 'react-bootstrap';

import type { Application, ITask } from '@spinnaker/core';
import { ConfirmationModalService, TaskExecutor } from '@spinnaker/core';

export interface IProxmoxInstanceActionsProps {
  app: Application;
  instance: {
    name: string;
    account: string;
    region: string;
    serverGroup?: string;
  };
}

/**
 * Instance-level lifecycle actions. Terminate and reboot ride orca's generic
 * terminateInstances/rebootInstances stages; start and stop use the proxmox-specific stages.
 */
export function ProxmoxInstanceActions({ app, instance }: IProxmoxInstanceActionsProps): JSX.Element {
  const submit = (type: string, description: string): PromiseLike<ITask> =>
    TaskExecutor.executeTask({
      job: [
        {
          type,
          instanceIds: [instance.name],
          region: instance.region,
          credentials: instance.account,
          cloudProvider: 'proxmox',
          serverGroupName: instance.serverGroup,
        },
      ],
      application: app,
      description: `${description}: ${instance.name}`,
    });

  const confirm = (verb: string, type: string, body?: string) =>
    ConfirmationModalService.confirm({
      header: `Really ${verb.toLowerCase()} ${instance.name}?`,
      buttonText: `${verb} ${instance.name}`,
      account: instance.account,
      body,
      taskMonitorConfig: {
        application: app,
        title: `${verb}ing ${instance.name}`,
        onTaskComplete: () => app.serverGroups.refresh(),
      },
      submitMethod: () => submit(type, `${verb} instance`),
      askForReason: true,
    });

  return (
    <Dropdown className="dropdown" id="instance-actions-dropdown">
      <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">Instance Actions</Dropdown.Toggle>
      <Dropdown.Menu className="dropdown-menu">
        <li>
          <a className="clickable" onClick={() => confirm('Start', 'startProxmoxInstances')}>
            Start
          </a>
        </li>
        <li>
          <a className="clickable" onClick={() => confirm('Stop', 'stopProxmoxInstances')}>
            Stop
          </a>
        </li>
        <li>
          <a className="clickable" onClick={() => confirm('Reboot', 'rebootInstances')}>
            Reboot
          </a>
        </li>
        <li role="presentation" className="divider" />
        <li>
          <a
            className="clickable"
            onClick={() =>
              confirm('Terminate', 'terminateInstances', '<p>Terminating stops and permanently deletes this VM.</p>')
            }
          >
            Terminate
          </a>
        </li>
      </Dropdown.Menu>
    </Dropdown>
  );
}
