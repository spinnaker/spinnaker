import React from 'react';
import { Dropdown } from 'react-bootstrap';

import type { Application, ILoadBalancer, ILoadBalancerDeleteCommand, IRouterInjectedProps } from '@spinnaker/core';
import { ConfirmationModalService, LoadBalancerWriter, withRouter } from '@spinnaker/core';

export interface ICloudFoundryLoadBalancerActionsProps {
  application: Application;
  loadBalancer: ILoadBalancer;
}

export class CloudFoundryLoadBalancerActionsComponent extends React.Component<
  ICloudFoundryLoadBalancerActionsProps & IRouterInjectedProps
> {
  private deleteLoadBalancer = () => {
    const { application, loadBalancer } = this.props;
    const taskMonitor = {
      application: application,
      title: 'Deleting ' + loadBalancer.name,
      onTaskComplete: () => {
        if (this.props.stateService.includes('**.serverGroup', { instanceId: loadBalancer.name })) {
          this.props.stateService.go('^');
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

export const CloudFoundryLoadBalancerActions = withRouter(CloudFoundryLoadBalancerActionsComponent);
