'use strict';

import { module } from 'angular';

export const AZURE_SERVERGROUP_CONFIGURE_WIZARD_LOADBALANCERS_SERVERGROUPLOADBALANCERSSELECTOR_DIRECTIVE =
  'spinnaker.azure.serverGroup.configure.loadBalancer.directive';
export const name = AZURE_SERVERGROUP_CONFIGURE_WIZARD_LOADBALANCERS_SERVERGROUPLOADBALANCERSSELECTOR_DIRECTIVE; // for backwards compatibility
module(AZURE_SERVERGROUP_CONFIGURE_WIZARD_LOADBALANCERS_SERVERGROUPLOADBALANCERSSELECTOR_DIRECTIVE, []).directive(
  'azureServerGroupLoadBalancersSelector',
  [
    'azureServerGroupConfigurationService',
    function () {
      return {
        restrict: 'E',
        scope: {
          command: '=',
        },
        templateUrl: require('./serverGroupLoadBalancersSelector.directive.html'),
      };
    },
  ],
);
