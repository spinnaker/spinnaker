import { IComponentOptions, module, noop } from 'angular';

import { TITUS_SERVER_GROUP_CONFIGURATION_SERVICE } from '../serverGroup/configure/serverGroupConfiguration.service';

export const loadBalancerSelectorComponent: IComponentOptions = {
  bindings: {
    command: '=',
  },
  controller: noop,
  templateUrl: require('./loadBalancerSelector.component.html'),
};

export const TITUS_LOAD_BALANCER_SELECTOR =
  'spinnaker.titus.serverGroup.configure.wizard.loadBalancers.selector.component';
module(TITUS_LOAD_BALANCER_SELECTOR, [TITUS_SERVER_GROUP_CONFIGURATION_SERVICE]).component(
  'titusLoadBalancerSelector',
  loadBalancerSelectorComponent,
);
