'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.configure.loadBalancer.controller', [
  require('../../../../../core/modal/wizard/v2modalWizard.service.js'),
  require('../../../../../core/loadBalancer/loadBalancer.read.service.js'),
  require('../../../../../core/network/network.read.service.js'),
])
  .controller('azureServerGroupLoadBalancersCtrl', function($scope, loadBalancerReader, networkReader, v2modalWizardService) {
    v2modalWizardService.markClean('load-balancers');

    this.loadBalancerChanged = function(item) {
      $scope.command.viewState.networkSettingsConfigured = true;
      v2modalWizardService.markComplete('load-balancers');
      $scope.command.selectedVnetSubnets = [ ];

      loadBalancerReader.getLoadBalancerDetails('azure', $scope.command.credentials, $scope.command.region, item).then (function (LBs) {
        if (LBs && LBs.length === 1) {
          var selectedLoadBalancer = LBs[0];
          networkReader.listNetworksByProvider('azure').then(function(vnets) {
            vnets.map(function(selectedVnet) {
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
          });
        }
      });
    };
  });
