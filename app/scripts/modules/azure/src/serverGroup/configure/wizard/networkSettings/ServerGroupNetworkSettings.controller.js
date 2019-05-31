'use strict';

const angular = require('angular');

import { ModalWizard } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.azure.serverGroup.configure.networkSettings.controller', [])
  .controller('azureServerGroupNetworkSettingsCtrl', [
    '$scope',
    function($scope) {
      ModalWizard.markClean('network-settings');

      $scope.command.selectedVnet = {
        name: $scope.command.vnet,
      };

      $scope.command.selectedSubnet = $scope.command.subnet;

      this.vnetChanged = function(item) {
        $scope.command.vnet = item;
        $scope.command.subnet = $scope.command.selectedSubnet = null;
        $scope.command.selectedVnetSubnets = item.subnets.map(s => s.name);
      };

      this.networkSettingsChanged = function(item) {
        $scope.command.vnet = $scope.command.selectedVnet.name;
        $scope.command.subnet = item;
        ModalWizard.markComplete('network-settings');
      };

      this.getVnetName = function() {
        if ($scope.command.selectedVnet) {
          return $scope.command.selectedVnet.name;
        } else {
          return 'Virtual network was not selected';
        }
      };
    },
  ]);
