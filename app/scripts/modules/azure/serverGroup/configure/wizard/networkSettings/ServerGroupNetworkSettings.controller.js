'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.configure.networkSettings.controller', [
  require('../../../../../core/modal/wizard/v2modalWizard.service.js'),
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
