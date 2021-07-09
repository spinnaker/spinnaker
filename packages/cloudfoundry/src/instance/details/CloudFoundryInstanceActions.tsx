import { cloneDeep } from 'lodash';
import React from 'react';
import { Dropdown } from 'react-bootstrap';

import { Application, ConfirmationModalService, InstanceWriter, ReactInjector } from '@spinnaker/core';
import { ICloudFoundryInstance } from '../../domain';

export interface ICloudFoundryInstanceActionsProps {
  application: Application;
  instance: ICloudFoundryInstance;
}

export class CloudFoundryInstanceActions extends React.Component<ICloudFoundryInstanceActionsProps> {
  private terminateInstance = () => {
    const { application, instance } = this.props;
    const instanceClone = cloneDeep(instance) as any;
    instanceClone.placement = {};
    instanceClone.id = instance.name;
    const taskMonitor = {
      application: application,
      title: 'Terminating ' + instance.name,
      onTaskComplete() {
        if (ReactInjector.$state.includes('**.serverGroup', { instanceId: instance.name })) {
          ReactInjector.$state.go('^');
        }
      },
    };

    const submitMethod = () => {
      return InstanceWriter.terminateInstance(instance, application, { cloudProvider: 'cloudfoundry' });
    };

    ConfirmationModalService.confirm({
      header: 'Really terminate ' + instance.name + '?',
      buttonText: 'Terminate',
      account: instance.account,
      taskMonitorConfig: taskMonitor,
      submitMethod,
    });
  };

  public render(): JSX.Element {
    return (
      <div className="actions">
        <Dropdown className="dropdown" id="instance-actions-dropdown">
          <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">Instance Actions</Dropdown.Toggle>
          <Dropdown.Menu className="dropdown-menu">
            <li>
              <a className="clickable" onClick={this.terminateInstance}>
                Terminate
              </a>
            </li>
          </Dropdown.Menu>
        </Dropdown>
      </div>
    );
  }
}
