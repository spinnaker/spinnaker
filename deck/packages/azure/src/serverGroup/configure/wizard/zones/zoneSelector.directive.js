'use strict';

import { module } from 'angular';

export const AZURE_SERVERGROUP_CONFIGURE_WIZARD_ZONES_ZONESELECTOR_DIRECTIVE =
  'spinnaker.azure.serverGroup.configure.wizard.capacity.zone.directive';
export const name = AZURE_SERVERGROUP_CONFIGURE_WIZARD_ZONES_ZONESELECTOR_DIRECTIVE; // for backwards compatibility
module(AZURE_SERVERGROUP_CONFIGURE_WIZARD_ZONES_ZONESELECTOR_DIRECTIVE, []).directive('azureZoneSelector', function () {
  return {
    restrict: 'E',
    templateUrl: require('./zoneSelector.directive.html'),
    scope: {},
    bindToController: {
      command: '=',
    },
    controllerAs: 'vm',
    controller: [
      '$scope',
      function ($scope) {
        this.updateEnableInboundNAT = () => {
          if ($scope.vm.command.zonesEnabled) {
            $scope.vm.command.enableInboundNAT = false;
          }
        };
      },
    ],
  };
});
