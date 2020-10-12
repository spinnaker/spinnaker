'use strict';

import { module } from 'angular';

import { ModalWizard } from '@spinnaker/core';

export const AZURE_SERVERGROUP_CONFIGURE_WIZARD_NETWORKSETTINGS_SERVERGROUPNETWORKSETTINGS_CONTROLLER =
  'spinnaker.azure.serverGroup.configure.networkSettings.controller';
export const name = AZURE_SERVERGROUP_CONFIGURE_WIZARD_NETWORKSETTINGS_SERVERGROUPNETWORKSETTINGS_CONTROLLER; // for backwards compatibility
module(AZURE_SERVERGROUP_CONFIGURE_WIZARD_NETWORKSETTINGS_SERVERGROUPNETWORKSETTINGS_CONTROLLER, []).controller(
  'azureServerGroupNetworkSettingsCtrl',
  [
    '$scope',
    function ($scope) {
      ModalWizard.markClean('network-settings');

      $scope.command.selectedVnet = {
        name: $scope.command.vnet,
      };

      $scope.command.selectedSubnet = $scope.command.subnet;

      this.vnetChanged = function (item) {
        $scope.command.vnet = item;
        $scope.command.subnet = $scope.command.selectedSubnet = null;
        $scope.command.selectedVnetSubnets = item.subnets.map((s) => s.name);
      };

      this.networkSettingsChanged = function (item) {
        $scope.command.vnet = $scope.command.selectedVnet.name;
        $scope.command.subnet = item;
        ModalWizard.markComplete('network-settings');
      };

      this.getVnetName = function () {
        if ($scope.command.selectedVnet) {
          return $scope.command.selectedVnet.name;
        } else {
          return 'Virtual network was not selected';
        }
      };
    },
  ],
);
