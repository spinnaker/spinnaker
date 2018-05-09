import { IController, IComponentOptions, module } from 'angular';

import {
  AWS_SERVER_GROUP_CONFIGURATION_SERVICE,
  AwsServerGroupConfigurationService,
} from 'amazon/serverGroup/configure/serverGroupConfiguration.service';

class LoadBalancerSelectorController implements IController {
  public command: any;

  public refreshed: boolean;
  public refreshing = false;

  constructor(private awsServerGroupConfigurationService: AwsServerGroupConfigurationService) {
    'ngInject';
  }

  public refreshLoadBalancers(): void {
    this.refreshing = true;
    this.awsServerGroupConfigurationService.refreshLoadBalancers(this.command).then(() => {
      this.refreshing = false;
      this.refreshed = true;
    });
  }
}

export class LoadBalancerSelectorComponent implements IComponentOptions {
  public bindings: any = {
    command: '=',
  };
  public controller: any = LoadBalancerSelectorController;
  public templateUrl = require('./loadBalancerSelector.component.html');
}

export const LOAD_BALANCER_SELECTOR = 'spinnaker.amazon.serverGroup.configure.wizard.loadBalancers.selector.component';
module(LOAD_BALANCER_SELECTOR, [AWS_SERVER_GROUP_CONFIGURATION_SERVICE]).component(
  'awsServerGroupLoadBalancerSelector',
  new LoadBalancerSelectorComponent(),
);
