'use strict';

import { module } from 'angular';

export const KUBERNETES_V1_LOADBALANCER_CONFIGURE_WIZARD_ADVANCEDSETTINGS_CONTROLLER =
  'spinnaker.loadBalancer.configure.kubernetes.advancedSettings';
export const name = KUBERNETES_V1_LOADBALANCER_CONFIGURE_WIZARD_ADVANCEDSETTINGS_CONTROLLER; // for backwards compatibility
module(KUBERNETES_V1_LOADBALANCER_CONFIGURE_WIZARD_ADVANCEDSETTINGS_CONTROLLER, []).controller(
  'kubernetesLoadBalancerAdvancedSettingsController',
  [
    '$scope',
    function($scope) {
      this.addExternalIp = function() {
        $scope.loadBalancer.externalIps.push({});
      };

      this.removeExternalIp = function(index) {
        $scope.loadBalancer.externalIps.splice(index, 1);
      };

      this.sessionAffinities = ['None', 'ClientIP'];
      this.types = ['ClusterIP', 'LoadBalancer', 'NodePort'];
    },
  ],
);
