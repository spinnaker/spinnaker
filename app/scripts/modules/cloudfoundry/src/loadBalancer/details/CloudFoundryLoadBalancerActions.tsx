import React from 'react';

import { Dropdown } from 'react-bootstrap';

import {
  Application,
  ConfirmationModalService,
  ILoadBalancer,
  ILoadBalancerDeleteCommand,
  LoadBalancerWriter,
  ReactInjector,
} from '@spinnaker/core';

export interface ICloudFoundryLoadBalancerActionsProps {
  application: Application;
  loadBalancer: ILoadBalancer;
}

export class CloudFoundryLoadBalancerActions extends React.Component<ICloudFoundryLoadBalancerActionsProps> {
  private deleteLoadBalancer = () => {
    const { application, loadBalancer } = this.props;
    const taskMonitor = {
      application: application,
      title: 'Deleting ' + loadBalancer.name,
      onTaskComplete() {
        if (ReactInjector.$state.includes('**.serverGroup', { instanceId: loadBalancer.name })) {
          ReactInjector.$state.go('^');
        }
      },
    };

    const submitMethod = () => {
      const loadBalancerDeleteCommand: ILoadBalancerDeleteCommand = {
        cloudProvider: loadBalancer.cloudProvider,
        credentials: loadBalancer.account,
        regions: [loadBalancer.region],
        loadBalancerName: loadBalancer.name,
      };
      return LoadBalancerWriter.deleteLoadBalancer(loadBalancerDeleteCommand, application);
    };

    ConfirmationModalService.confirm({
      header: 'Really delete ' + loadBalancer.name + '?',
      buttonText: 'Delete ' + loadBalancer.name,
      account: loadBalancer.account,
      taskMonitorConfig: taskMonitor,
      submitMethod,
    });
  };

  public render(): JSX.Element {
    return (
      <div className="actions">
        <Dropdown className="dropdown" id="instance-actions-dropdown">
          <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">Load Balancer Actions</Dropdown.Toggle>
          <Dropdown.Menu className="dropdown-menu">
            <li>
              <a className="clickable" onClick={this.deleteLoadBalancer}>
                Delete Load Balancer
              </a>
            </li>
          </Dropdown.Menu>
        </Dropdown>
      </div>
    );
  }
}
