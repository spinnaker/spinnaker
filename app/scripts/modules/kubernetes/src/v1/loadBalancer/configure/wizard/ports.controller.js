'use strict';

import { module } from 'angular';

export const KUBERNETES_V1_LOADBALANCER_CONFIGURE_WIZARD_PORTS_CONTROLLER =
  'spinnaker.loadBalancer.configure.kubernetes.ports';
export const name = KUBERNETES_V1_LOADBALANCER_CONFIGURE_WIZARD_PORTS_CONTROLLER; // for backwards compatibility
module(KUBERNETES_V1_LOADBALANCER_CONFIGURE_WIZARD_PORTS_CONTROLLER, []).controller(
  'kubernetesLoadBalancerPortsController',
  [
    '$scope',
    function($scope) {
      this.addPort = function() {
        $scope.loadBalancer.ports.push({});
      };

      this.removePort = function(index) {
        $scope.loadBalancer.ports.splice(index, 1);
      };

      this.protocols = ['TCP', 'UDP'];
      this.maxPort = 65535;
    },
  ],
);
