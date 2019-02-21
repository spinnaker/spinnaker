import { IController, IComponentOptions, module } from 'angular';

import { InfrastructureCaches } from '@spinnaker/core';

import {
  ECS_SERVER_GROUP_CONFIGURATION_SERVICE,
  EcsServerGroupConfigurationService,
} from '../../serverGroupConfiguration.service';

class LoadBalancerSelectorController implements IController {
  public command: any;

  public refreshTime: number;
  public refreshing = false;

  public static $inject = ['ecsServerGroupConfigurationService'];
  constructor(private ecsServerGroupConfigurationService: EcsServerGroupConfigurationService) {
    'ngInject';

    this.setLoadBalancerRefreshTime();
  }

  public setLoadBalancerRefreshTime(): void {
    this.refreshTime = InfrastructureCaches.get('loadBalancers').getStats().ageMax;
  }

  public refreshLoadBalancers(): void {
    this.refreshing = true;
    this.ecsServerGroupConfigurationService.refreshLoadBalancers(this.command).then(() => {
      this.refreshing = false;
      this.setLoadBalancerRefreshTime();
    });
  }
}

export class ApplicationLoadBalancerSelectorComponent implements IComponentOptions {
  public bindings: any = {
    command: '=',
  };
  public controller: any = LoadBalancerSelectorController;
  public templateUrl = require('./networkingSelector.component.html');
}

export const ECS_NETWORKING_SECTION = 'spinnaker.ecs.serverGroup.configure.wizard.loadBalancers.selector.component';
module(ECS_NETWORKING_SECTION, [ECS_SERVER_GROUP_CONFIGURATION_SERVICE]).component(
  'ecsServerGroupLoadBalancerSelector',
  new ApplicationLoadBalancerSelectorComponent(),
);
