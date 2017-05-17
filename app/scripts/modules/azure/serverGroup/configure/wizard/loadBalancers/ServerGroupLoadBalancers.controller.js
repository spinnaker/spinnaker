'use strict';

const angular = require('angular');

import {
  INFRASTRUCTURE_CACHE_SERVICE,
  LOAD_BALANCER_READ_SERVICE,
  NETWORK_READ_SERVICE,
  V2_MODAL_WIZARD_SERVICE
} from '@spinnaker/core';

module.exports = angular.module('spinnaker.azure.serverGroup.configure.loadBalancer.controller', [
  INFRASTRUCTURE_CACHE_SERVICE,
  V2_MODAL_WIZARD_SERVICE,
  LOAD_BALANCER_READ_SERVICE,
  NETWORK_READ_SERVICE,
])
  .controller('azureServerGroupLoadBalancersCtrl', function($scope, infrastructureCaches, loadBalancerReader, networkReader, v2modalWizardService) {
    v2modalWizardService.markClean('load-balancers');

    this.loadBalancerChanged = function(item) {
      $scope.command.viewState.networkSettingsConfigured = true;
      v2modalWizardService.markComplete('load-balancers');
      $scope.command.selectedVnetSubnets = [ ];
      infrastructureCaches.clearCache('networks');

      loadBalancerReader.getLoadBalancerDetails('azure', $scope.command.credentials, $scope.command.region, item).then (function (LBs) {
        if (LBs && LBs.length === 1) {
          var selectedLoadBalancer = LBs[0];
          networkReader.listNetworks().then(function(vnets) {
            if (vnets.azure) {
              vnets.azure.forEach((selectedVnet) => {
                if (selectedVnet.account === $scope.command.credentials && selectedVnet.region === $scope.command.region && selectedVnet.name == selectedLoadBalancer.vnet) {
                  $scope.command.selectedVnet = selectedVnet;
                  selectedVnet.subnets.map(function(subnet) {
                    var addSubnet = true;
                    if (subnet.devices) {
                      subnet.devices.map(function(device) {
                        // only add subnets that are not assigned to an ApplicationGateway
                        if (device && device.type === 'applicationGateways') {
                          addSubnet = false;
                        }
                      });
                    }
                    if (addSubnet) {
                      $scope.command.selectedVnetSubnets.push(subnet.name);
                    }
                  });
                }
              });
            }
          });
        }
      });
    };
  });
