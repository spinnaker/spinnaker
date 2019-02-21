'use strict';

const angular = require('angular');

import { InfrastructureCaches, LOAD_BALANCER_READ_SERVICE, NetworkReader, ModalWizard } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.azure.serverGroup.configure.loadBalancer.controller', [LOAD_BALANCER_READ_SERVICE])
  .controller('azureServerGroupLoadBalancersCtrl', [
    '$scope',
    'loadBalancerReader',
    function($scope, loadBalancerReader) {
      ModalWizard.markClean('load-balancers');

      if ($scope.command.credentials && $scope.command.region && $scope.command.loadBalancerName) {
        $scope.command.viewState.networkSettingsConfigured = true;
      }

      this.loadBalancerChanged = function(item) {
        $scope.command.viewState.networkSettingsConfigured = true;
        ModalWizard.markComplete('load-balancers');
        $scope.command.selectedVnetSubnets = [];
        $scope.command.selectedSubnet = null;
        InfrastructureCaches.clearCache('networks');

        loadBalancerReader
          .getLoadBalancerDetails('azure', $scope.command.credentials, $scope.command.region, item)
          .then(function(LBs) {
            if (LBs && LBs.length === 1) {
              var selectedLoadBalancer = LBs[0];
              NetworkReader.listNetworks().then(function(vnets) {
                if (vnets.azure) {
                  vnets.azure.forEach(selectedVnet => {
                    if (
                      selectedVnet.account === $scope.command.credentials &&
                      selectedVnet.region === $scope.command.region &&
                      selectedVnet.name == selectedLoadBalancer.vnet
                    ) {
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
    },
  ]);
