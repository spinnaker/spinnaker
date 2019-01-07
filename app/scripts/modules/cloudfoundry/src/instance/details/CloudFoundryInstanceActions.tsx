import * as React from 'react';

import { Dropdown } from 'react-bootstrap';
import { cloneDeep } from 'lodash';

import { Application, ConfirmationModalService, InstanceWriter, ReactInjector } from '@spinnaker/core';

import { ICloudFoundryInstance } from 'cloudfoundry/domain';

export interface ICloudFoundryInstanceActionsProps {
  application: Application;
  confirmationModalService: ConfirmationModalService;
  instance: ICloudFoundryInstance;
  instanceWriter: InstanceWriter;
}

export class CloudFoundryInstanceActions extends React.Component<ICloudFoundryInstanceActionsProps> {
  private terminateInstance = () => {
    const { application, confirmationModalService, instance, instanceWriter } = this.props;
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
      return instanceWriter.terminateInstance(instance, application, { cloudProvider: 'cloudfoundry' });
    };

    confirmationModalService.confirm({
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
