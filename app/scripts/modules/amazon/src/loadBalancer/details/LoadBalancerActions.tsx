import * as React from 'react';
import { Dropdown } from 'react-bootstrap';
import { get } from 'lodash';

import {
  Application,
  ApplicationReader,
  LoadBalancerWriter,
  SETTINGS,
  NgReact,
  ReactInjector,
  HelpField,
} from '@spinnaker/core';

import { IAmazonLoadBalancer, IAmazonLoadBalancerDeleteCommand } from 'amazon/domain';

import { ILoadBalancerFromStateParams } from './loadBalancerDetails.controller';
import { LoadBalancerTypes } from '../configure/LoadBalancerTypes';

export interface ILoadBalancerActionsProps {
  app: Application;
  loadBalancer: IAmazonLoadBalancer;
  loadBalancerFromParams: ILoadBalancerFromStateParams;
}

export interface ILoadBalancerActionsState {
  application: Application;
}

export class LoadBalancerActions extends React.Component<ILoadBalancerActionsProps, ILoadBalancerActionsState> {
  constructor(props: ILoadBalancerActionsProps) {
    super(props);

    const { app, loadBalancer } = this.props;

    let application: Application;

    const loadBalancerAppName = loadBalancer.name.split('-')[0];
    if (loadBalancerAppName === app.name) {
      // Name matches the currently active application
      application = app;
    } else {
      // Load balancer is a part of a different application
      ApplicationReader.getApplication(loadBalancerAppName)
        .then(loadBalancerApp => {
          this.setState({ application: loadBalancerApp });
        })
        .catch(() => {
          // If the application can't be found, just use the old one
          this.setState({ application: this.props.app });
        });
    }

    this.state = {
      application,
    };
  }

  public editLoadBalancer = (): void => {
    const { loadBalancer } = this.props;
    const { application } = this.state;
    const LoadBalancerModal = LoadBalancerTypes.find(t => t.type === loadBalancer.loadBalancerType).component;
    LoadBalancerModal.show({ app: application, loadBalancer });
  };

  public deleteLoadBalancer = (): void => {
    const { app, loadBalancer, loadBalancerFromParams } = this.props;

    if (loadBalancer.instances && loadBalancer.instances.length) {
      return;
    }

    const taskMonitor = {
      application: app,
      title: 'Deleting ' + loadBalancerFromParams.name,
    };

    const command: IAmazonLoadBalancerDeleteCommand = {
      cloudProvider: loadBalancer.cloudProvider,
      loadBalancerName: loadBalancer.name,
      loadBalancerType: loadBalancer.loadBalancerType || 'classic',
      regions: [loadBalancer.region],
      credentials: loadBalancer.account,
      vpcId: get(loadBalancer, 'elb.vpcId', null),
    };

    const submitMethod = () => LoadBalancerWriter.deleteLoadBalancer(command, app);

    ReactInjector.confirmationModalService.confirm({
      header: `Really delete ${loadBalancerFromParams.name} in ${loadBalancerFromParams.region}: ${
        loadBalancerFromParams.accountId
      }?`,
      buttonText: `Delete ${loadBalancerFromParams.name}`,
      provider: 'aws',
      account: loadBalancerFromParams.accountId,
      applicationName: app.name,
      taskMonitorConfig: taskMonitor,
      submitMethod,
    });
  };

  private entityTagUpdate = (): void => {
    this.props.app.loadBalancers.refresh();
  };

  public render() {
    const { app, loadBalancer } = this.props;
    const { application } = this.state;

    const { AddEntityTagLinks } = NgReact;

    return (
      <div style={{ display: 'inline-block' }}>
        <Dropdown className="dropdown" id="load-balancer-actions-dropdown">
          <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">
            <span>Load Balancer Actions</span>
          </Dropdown.Toggle>
          <Dropdown.Menu className="dropdown-menu">
            <li className={!application ? 'disabled' : ''}>
              <a className="clickable" onClick={this.editLoadBalancer}>
                Edit Load Balancer
              </a>
            </li>
            {!loadBalancer.instances.length && (
              <li>
                <a className="clickable" onClick={this.deleteLoadBalancer}>
                  Delete Load Balancer
                </a>
              </li>
            )}
            {loadBalancer.instances.length > 0 && (
              <li className="disabled">
                <a className="clickable" onClick={this.deleteLoadBalancer}>
                  Delete Load Balancer{' '}
                  <HelpField content="You must detach all instances before you can delete this load balancer." />
                </a>
              </li>
            )}
            {SETTINGS &&
              SETTINGS.feature.entityTags && (
                <AddEntityTagLinks
                  component={loadBalancer}
                  application={app}
                  entityType="loadBalancer"
                  onUpdate={this.entityTagUpdate}
                />
              )}
          </Dropdown.Menu>
        </Dropdown>
      </div>
    );
  }
}
