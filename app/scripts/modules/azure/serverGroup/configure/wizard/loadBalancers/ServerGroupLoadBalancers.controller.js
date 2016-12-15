'use strict';

let angular = require('angular');
import {V2_MODAL_WIZARD_SERVICE} from 'core/modal/wizard/v2modalWizard.service';
import {NETWORK_READ_SERVICE} from 'core/network/network.read.service';
import {INFRASTRUCTURE_CACHE_SERVICE} from 'core/cache/infrastructureCaches.service';

module.exports = angular.module('spinnaker.azure.serverGroup.configure.loadBalancer.controller', [
  INFRASTRUCTURE_CACHE_SERVICE,
  V2_MODAL_WIZARD_SERVICE,
  require('core/loadBalancer/loadBalancer.read.service.js'),
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
