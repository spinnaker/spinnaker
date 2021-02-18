'use strict';

import { module } from 'angular';

import { InfrastructureCaches, LOAD_BALANCER_READ_SERVICE, ModalWizard, NetworkReader } from '@spinnaker/core';

import Utility from '../../../../utility';

export const AZURE_SERVERGROUP_CONFIGURE_WIZARD_LOADBALANCERS_SERVERGROUPLOADBALANCERS_CONTROLLER =
  'spinnaker.azure.serverGroup.configure.loadBalancer.controller';
export const name = AZURE_SERVERGROUP_CONFIGURE_WIZARD_LOADBALANCERS_SERVERGROUPLOADBALANCERS_CONTROLLER; // for backwards compatibility
module(AZURE_SERVERGROUP_CONFIGURE_WIZARD_LOADBALANCERS_SERVERGROUPLOADBALANCERS_CONTROLLER, [
  LOAD_BALANCER_READ_SERVICE,
]).controller('azureServerGroupLoadBalancersCtrl', [
  '$scope',
  'loadBalancerReader',
  function ($scope, loadBalancerReader) {
    ModalWizard.markClean('load-balancers');

    function loadVnetSubnets(item, type) {
      loadBalancerReader
        .getLoadBalancerDetails('azure', $scope.command.credentials, $scope.command.region, item)
        .then(function (LBs) {
          if (LBs && LBs.length === 1) {
            const selectedLoadBalancer = LBs[0];
            const attachedVnet = $scope.command.selectedVnet;
            $scope.command.selectedVnet = null;
            $scope.command.selectedVnetSubnets = [];
            $scope.command.allVnets = [];
            NetworkReader.listNetworks().then(function (vnets) {
              if (vnets.azure) {
                vnets.azure.forEach((selectedVnet) => {
                  if (
                    selectedVnet.account === $scope.command.credentials &&
                    selectedVnet.region === $scope.command.region
                  ) {
                    $scope.command.allVnets.push(selectedVnet);
                  }
                  if (
                    selectedVnet.account === $scope.command.credentials &&
                    selectedVnet.region === $scope.command.region &&
                    ((type === 'Azure Application Gateway' && selectedVnet.name == selectedLoadBalancer.vnet) ||
                      (type === 'Azure Load Balancer' && selectedVnet.name === attachedVnet.name))
                  ) {
                    $scope.command.selectedVnet = selectedVnet;
                    selectedVnet.subnets.map(function (subnet) {
                      let addSubnet = true;
                      if (subnet.devices) {
                        subnet.devices.map(function (device) {
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
    }

    if ($scope.command.credentials && $scope.command.region && $scope.command.loadBalancerName) {
      $scope.command.viewState.networkSettingsConfigured = true;
      $scope.command.selectedVnetSubnets = [];
      loadVnetSubnets($scope.command.loadBalancerName, $scope.command.loadBalancerType);
    }

    this.loadBalancerChanged = function (item) {
      $scope.command.viewState.networkSettingsConfigured = true;
      ModalWizard.markComplete('load-balancers');

      const loadBalancers = $scope.command.backingData.loadBalancers;
      let loadBalancerType = null;

      if (loadBalancers) {
        const loadBalancerToFind = loadBalancers.find((lb) => lb.name === item);
        if (loadBalancerToFind) {
          loadBalancerType = Utility.getLoadBalancerType(loadBalancerToFind.loadBalancerType).type;
        }
      }

      $scope.command.selectedVnetSubnets = [];
      $scope.command.selectedSubnet = null;
      $scope.command.loadBalancerType = loadBalancerType;
      InfrastructureCaches.clearCache('networks');
      loadVnetSubnets(item, loadBalancerType);
    };
  },
]);
