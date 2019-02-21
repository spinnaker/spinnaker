import { IController, IComponentOptions, module } from 'angular';

import { InfrastructureCaches } from '@spinnaker/core';
import {
  TITUS_SERVER_GROUP_CONFIGURATION_SERVICE,
  TitusServerGroupConfigurationService,
} from '../serverGroup/configure/serverGroupConfiguration.service';

class LoadBalancerSelectorController implements IController {
  public command: any;

  public refreshTime: number;
  public refreshing = false;

  public static $inject = ['titusServerGroupConfigurationService'];
  public constructor(private titusServerGroupConfigurationService: TitusServerGroupConfigurationService) {}

  public $onInit(): void {
    this.refreshing = true;
    this.titusServerGroupConfigurationService.refreshLoadBalancers(this.command).then(() => (this.refreshing = false));
    this.setLoadBalancerRefreshTime();
  }

  public setLoadBalancerRefreshTime(): void {
    this.refreshTime = InfrastructureCaches.get('loadBalancers').getStats().ageMax;
  }
}

export const loadBalancerSelectorComponent: IComponentOptions = {
  bindings: {
    command: '=',
  },
  controller: LoadBalancerSelectorController,
  templateUrl: require('./loadBalancerSelector.component.html')
};

export const TITUS_LOAD_BALANCER_SELECTOR =
  'spinnaker.titus.serverGroup.configure.wizard.loadBalancers.selector.component';
module(TITUS_LOAD_BALANCER_SELECTOR, [TITUS_SERVER_GROUP_CONFIGURATION_SERVICE]).component(
  'titusLoadBalancerSelector',
  loadBalancerSelectorComponent,
);
