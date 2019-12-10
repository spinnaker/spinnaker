'use strict';

import { module } from 'angular';

export const KUBERNETES_V1_SERVERGROUP_CONFIGURE_WIZARD_ADVANCEDSETTINGS_CONTROLLER =
  'spinnaker.serverGroup.configure.kubernetes.advancedSettings';
export const name = KUBERNETES_V1_SERVERGROUP_CONFIGURE_WIZARD_ADVANCEDSETTINGS_CONTROLLER; // for backwards compatibility
module(KUBERNETES_V1_SERVERGROUP_CONFIGURE_WIZARD_ADVANCEDSETTINGS_CONTROLLER, []).controller(
  'kubernetesServerGroupAdvancedSettingsController',
  [
    '$scope',
    function($scope) {
      if (!$scope.command.dnsPolicy) {
        $scope.command.dnsPolicy = 'ClusterFirst';
      }

      this.policies = ['ClusterFirst', 'Default', 'ClusterFirstWithHostNet'];

      this.onTolerationChange = tolerations => {
        $scope.command.tolerations = tolerations;
        $scope.$applyAsync();
      };
    },
  ],
);
