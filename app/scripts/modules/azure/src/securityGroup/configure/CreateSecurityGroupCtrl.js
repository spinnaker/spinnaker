'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { module } from 'angular';
import _ from 'lodash';

import { AccountService, FirewallLabels, NetworkReader, TaskMonitor } from '@spinnaker/core';

import { AZURE_SECURITYGROUP_SECURITYGROUP_WRITE_SERVICE } from '../securityGroup.write.service';

export const AZURE_SECURITYGROUP_CONFIGURE_CREATESECURITYGROUPCTRL = 'spinnaker.azure.securityGroup.create.controller';
export const name = AZURE_SECURITYGROUP_CONFIGURE_CREATESECURITYGROUPCTRL; // for backwards compatibility
module(AZURE_SECURITYGROUP_CONFIGURE_CREATESECURITYGROUPCTRL, [
  UIROUTER_ANGULARJS,
  AZURE_SECURITYGROUP_SECURITYGROUP_WRITE_SERVICE,
]).controller('azureCreateSecurityGroupCtrl', [
  '$scope',
  '$uibModalInstance',
  '$state',
  '$controller',
  'application',
  'securityGroup',
  'azureSecurityGroupWriter',
  function ($scope, $uibModalInstance, $state, $controller, application, securityGroup, azureSecurityGroupWriter) {
    $scope.pages = {
      location: require('./createSecurityGroupProperties.html'),
      ingress: require('./createSecurityGroupIngress.html'),
    };

    $scope.regions = [];

    $scope.firewallLabel = FirewallLabels.get('Firewall');

    const ctrl = this;
    $scope.isNew = true;
    $scope.state = {
      submitting: false,
      infiniteScroll: {
        numToAdd: 20,
        currentItems: 20,
      },
    };

    AccountService.listAccounts('azure').then(function (accounts) {
      $scope.accounts = accounts;
      ctrl.accountUpdated();
    });

    ctrl.addMoreItems = function () {
      $scope.state.infiniteScroll.currentItems += $scope.state.infiniteScroll.numToAdd;
    };

    function onApplicationRefresh() {
      // If the user has already closed the modal, do not navigate to the new details view
      if ($scope.$$destroyed) {
        return;
      }
      $uibModalInstance.close();
      const newStateParams = {
        name: $scope.securityGroup.name,
        accountId: $scope.securityGroup.credentials || $scope.securityGroup.accountName,
        region: $scope.securityGroup.regions[0],
        provider: 'azure',
      };
      if (!$state.includes('**.firewallDetails')) {
        $state.go('.firewallDetails', newStateParams);
      } else {
        $state.go('^.firewallDetails', newStateParams);
      }
    }

    function onTaskComplete() {
      application.securityGroups.refresh();
      application.securityGroups.onNextRefresh($scope, onApplicationRefresh);
    }

    $scope.taskMonitor = new TaskMonitor({
      application: application,
      title: `Creating your ${FirewallLabels.get('firewall')}`,
      modalInstance: $uibModalInstance,
      onTaskComplete: onTaskComplete,
    });

    $scope.securityGroup = securityGroup;

    ctrl.accountUpdated = function () {
      AccountService.getRegionsForAccount($scope.securityGroup.credentials).then(function (regions) {
        $scope.regions = regions;
        $scope.securityGroup.regions = regions;
        ctrl.updateName();
        ctrl.regionUpdated();
      });
    };

    this.regionUpdated = function () {
      ctrl.vnetUpdated();
    };

    this.vnetUpdated = function () {
      const account = $scope.securityGroup.credentials;
      const region = $scope.securityGroup.region;
      $scope.securityGroup.selectedVnet = null;
      $scope.securityGroup.vnet = null;
      $scope.securityGroup.vnetResourceGroup = null;

      ctrl.selectedVnets = [];

      NetworkReader.listNetworks().then(function (vnets) {
        if (vnets.azure) {
          vnets.azure.forEach((vnet) => {
            if (vnet.account === account && vnet.region === region) {
              ctrl.selectedVnets.push(vnet);
            }
          });
        }
      });

      ctrl.subnetUpdated();
    };

    this.subnetUpdated = function () {
      $scope.securityGroup.selectedSubnet = null;
      $scope.securityGroup.subnet = null;
      ctrl.selectedSubnets = [];
    };

    this.selectedVnetChanged = function (item) {
      $scope.securityGroup.vnet = item.name;
      $scope.securityGroup.vnetResourceGroup = item.resourceGroup;
      $scope.securityGroup.selectedSubnet = null;
      $scope.securityGroup.subnet = null;
      ctrl.selectedSubnets = [];
      if (item.subnets) {
        item.subnets.map(function (subnet) {
          ctrl.selectedSubnets.push(subnet);
        });
      }
    };

    ctrl.cancel = function () {
      $uibModalInstance.dismiss();
    };

    ctrl.updateName = function () {
      const securityGroup = $scope.securityGroup;
      let name = application.name;
      if (securityGroup.detail) {
        name += '-' + securityGroup.detail;
      }
      securityGroup.name = name;
      $scope.namePreview = name;
    };

    ctrl.upsert = function () {
      $scope.taskMonitor.submit(function () {
        const params = {
          cloudProvider: 'azure',
          appName: application.name,
          region: $scope.securityGroup.region,
          vpcId: 'null',
        };

        if ($scope.securityGroup.selectedVnet) {
          $scope.securityGroup.vnet = $scope.securityGroup.selectedVnet.name;
          $scope.securityGroup.vnetResourceGroup = $scope.securityGroup.selectedVnet.resourceGroup;
        }

        if ($scope.securityGroup.selectedSubnet) {
          $scope.securityGroup.subnet = $scope.securityGroup.selectedSubnet.name;
        }

        $scope.securityGroup.type = 'upsertSecurityGroup';

        return azureSecurityGroupWriter.upsertSecurityGroup($scope.securityGroup, application, 'Create', params);
      });
    };

    ctrl.addRule = function (ruleset) {
      ruleset.push({
        name: $scope.securityGroup.name + '-Rule' + ruleset.length,
        priority: ruleset.length == 0 ? 100 : 100 * (ruleset.length + 1),
        protocolUI: 'tcp',
        protocol: 'tcp',
        access: 'Allow',
        direction: 'InBound',
        sourceAddressPrefix: '*',
        sourceAddressPrefixes: [],
        sourcePortRange: '*',
        destinationAddressPrefix: '*',
        destinationPortRange: '*',
        destinationPortRanges: [],
        destPortRanges: '*',
        sourceIPCIDRRanges: '*',
      });
    };

    ctrl.portUpdated = function (ruleset, index) {
      if (!_.isEmpty(ruleset[index].destPortRanges)) {
        const ruleRanges = ruleset[index].destPortRanges.split(',');

        if (ruleRanges.length > 1) {
          ruleset[index].destinationPortRanges = [];
          ruleRanges.forEach((v) => ruleset[index].destinationPortRanges.push(v));

          // If there are multiple port ranges then set null to the single port parameter otherwise ARM template will fail in validation.
          ruleset[index].destinationPortRange = null;
        } else {
          ruleset[index].destinationPortRange = ruleset[index].destPortRanges;

          // If there is a single port range then set null to the port array otherwise ARM template will fail in validation.
          ruleset[index].destinationPortRanges = [];
        }
      }
    };

    ctrl.sourceIPCIDRUpdated = function (ruleset, index) {
      if (!_.isEmpty(ruleset[index].destPortRanges)) {
        const ruleRanges = ruleset[index].sourceIPCIDRRanges.split(',');
        if (ruleRanges.length > 1) {
          ruleset[index].sourceAddressPrefixes = [];
          ruleRanges.forEach((v) => ruleset[index].sourceAddressPrefixes.push(v));

          // If there are multiple IP/CIDR ranges then set null to the single sourceAddressPrefix parameter otherwise ARM template will fail in validation
          ruleset[index].sourceAddressPrefix = null;
        } else {
          ruleset[index].sourceAddressPrefix = ruleset[index].sourceIPCIDRRanges;

          // If there is a single IP/CIDR then set null to the IP/CIDR array otherwise ARM template will fail in validation.
          ruleset[index].sourceAddressPrefixes = [];
        }
      }
    };

    ctrl.protocolUpdated = function (ruleset, index) {
      ruleset[index].protocol = ruleset[index].protocolUI;
    };

    ctrl.removeRule = function (ruleset, index) {
      ruleset.splice(index, 1);
    };

    ctrl.moveUp = function (ruleset, index) {
      if (index === 0) return;
      swapRules(ruleset, index, index - 1);
    };
    ctrl.moveDown = function (ruleset, index) {
      if (index === ruleset.length - 1) return;
      swapRules(ruleset, index, index + 1);
    };

    function swapRules(ruleset, a, b) {
      const temp = ruleset[b];
      const priorityA = ruleset[a].priority;
      const priorityB = ruleset[b].priority;
      //swap elements
      ruleset[b] = ruleset[a];
      ruleset[a] = temp;
      //swap priorities
      ruleset[a].priority = priorityA;
      ruleset[b].priority = priorityB;
    }

    $scope.securityGroup.securityRules = [];
  },
]);
