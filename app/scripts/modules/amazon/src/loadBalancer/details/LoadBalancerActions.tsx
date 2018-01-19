import { IModalSettings } from 'angular-ui-bootstrap';
import * as React from 'react';
import { Dropdown } from 'react-bootstrap';
import { cloneDeep, get } from 'lodash';
import { BindAll } from 'lodash-decorators';

import { Application, SETTINGS } from 'core';
import { NgReact, ReactInjector } from 'core/reactShims';

import { IAmazonLoadBalancer, IAmazonLoadBalancerDeleteCommand } from 'amazon';

import { ILoadBalancerFromStateParams } from './loadBalancerDetails.controller';
import { LoadBalancerTypes } from '../configure/choice/LoadBalancerTypes';

export interface ILoadBalancerActionsProps {
  app: Application;
  loadBalancer: IAmazonLoadBalancer;
  loadBalancerFromParams: ILoadBalancerFromStateParams;
}

@BindAll()
export class LoadBalancerActions extends React.Component<ILoadBalancerActionsProps> {
  public editLoadBalancer(): void {
    const { loadBalancer } = this.props;

    const wizard = LoadBalancerTypes.find(t => t.type === loadBalancer.loadBalancerType);

    let application = this.props.app;

    const modalOptions: IModalSettings = {
      templateUrl: wizard.editTemplateUrl,
      controller: `${wizard.controller} as ctrl`,
      size: 'lg',
      resolve: {
        application: () => application,
        loadBalancer: () => cloneDeep(loadBalancer),
        isNew: () => false,
        forPipelineConfig: () => false
      }
    };

    const loadBalancerAppName = loadBalancer.name.split('-')[0];

    if (loadBalancerAppName === application.name) {
      // Name matches the currently active application
      ReactInjector.modalService.open(modalOptions);
    } else {
      // Load balancer a part of a different application
      ReactInjector.applicationReader.getApplication(loadBalancerAppName)
        .then((loadBalancerApp) => {
          application = loadBalancerApp;
          ReactInjector.modalService.open(modalOptions);
        })
        .catch(() => {
          // If the application can't be found, just use the old one
          ReactInjector.modalService.open(modalOptions);
        });
    }
  }

  public deleteLoadBalancer(): void {
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

    const submitMethod = () => ReactInjector.loadBalancerWriter.deleteLoadBalancer(command, app);

    ReactInjector.confirmationModalService.confirm({
      header: `Really delete ${loadBalancerFromParams.name} in ${loadBalancerFromParams.region}: ${loadBalancerFromParams.accountId}?`,
      buttonText: `Delete ${loadBalancerFromParams.name}`,
      provider: 'aws',
      account: loadBalancerFromParams.accountId,
      applicationName: app.name,
      taskMonitorConfig: taskMonitor,
      submitMethod: submitMethod
    });
  }

  private entityTagUpdate(): void {
    this.props.app.loadBalancers.refresh();
  }

  public render() {
    const { app, loadBalancer } = this.props;

    const { AddEntityTagLinks } = NgReact;

    return (
      <Dropdown className="dropdown" id="load-balancer-actions-dropdown">
        <Dropdown.Toggle className="btn btn-sm btn-primary dropdown-toggle">
          <span>Load Balancer Actions</span>
        </Dropdown.Toggle>
        <Dropdown.Menu className="dropdown-menu">
          <li><a className="clickable" onClick={this.editLoadBalancer}>Edit Load Balancer</a></li>
          {!loadBalancer.instances.length && <li><a className="clickable" onClick={this.deleteLoadBalancer}>Delete Load Balancer</a></li>}
          {loadBalancer.instances.length > 0 && (
            <li className="disabled" uib-tooltip="You must detach all instances before you can delete this load balancer.">
              <a className="clickable" onClick={this.deleteLoadBalancer}>Delete Load Balancer</a>
            </li>
          )}
          {SETTINGS && SETTINGS.feature.entityTags && (
            <AddEntityTagLinks
              component={loadBalancer}
              application={app}
              entityType="loadBalancer"
              onUpdate={this.entityTagUpdate}
            />
          )}
        </Dropdown.Menu>
      </Dropdown>
    );
  }
}
