'use strict';

import {V2_MODAL_WIZARD_SERVICE} from 'core/modal/wizard/v2modalWizard.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.configure.networkSettings.controller', [
  V2_MODAL_WIZARD_SERVICE,
])
  .controller('azureServerGroupNetworkSettingsCtrl', function($scope, v2modalWizardService) {
    v2modalWizardService.markClean('network-settings');

    this.networkSettingsChanged = function(item) {
      $scope.command.vnet = $scope.command.selectedVnet.name;
      $scope.command.subnet = item;
      v2modalWizardService.markComplete('network-settings');
    };

    this.getVnetName = function() {
      if ($scope.command.selectedVnet) {
        return $scope.command.selectedVnet.name;
      } else {
        return 'Virtual network was not selected';
      }
    };
  });
