'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { AccountService, FirewallLabels, TaskMonitor } from '@spinnaker/core';

import { AZURE_SECURITYGROUP_CONFIGURE_CREATESECURITYGROUPCTRL } from '../configure/CreateSecurityGroupCtrl';
import { AZURE_SECURITYGROUP_SECURITYGROUP_WRITE_SERVICE } from '../securityGroup.write.service';

export const AZURE_SECURITYGROUP_CLONE_CLONESECURITYGROUP_CONTROLLER = 'spinnaker.azure.securityGroup.clone.controller';
export const name = AZURE_SECURITYGROUP_CLONE_CLONESECURITYGROUP_CONTROLLER; // for backwards compatibility
module(AZURE_SECURITYGROUP_CLONE_CLONESECURITYGROUP_CONTROLLER, [
  AZURE_SECURITYGROUP_SECURITYGROUP_WRITE_SERVICE,
  AZURE_SECURITYGROUP_CONFIGURE_CREATESECURITYGROUPCTRL,
]).controller('azureCloneSecurityGroupController', [
  '$scope',
  '$uibModalInstance',
  '$controller',
  '$state',
  'azureSecurityGroupWriter',
  'securityGroup',
  'application',
  function ($scope, $uibModalInstance, $controller, $state, azureSecurityGroupWriter, securityGroup, application) {
    const ctrl = this;

    $scope.firewallLabel = FirewallLabels.get('Firewall');

    $scope.pages = {
      location: require('../configure/createSecurityGroupProperties.html'),
      ingress: require('../configure/createSecurityGroupIngress.html'),
    };

    securityGroup.securityRules = _.map(securityGroup.securityRules, function (rule) {
      const temp = rule.destinationPortRange.split('-');
      rule.startPort = Number(temp[0]);
      rule.endPort = Number(temp[1]);
      return rule;
    });

    ctrl.accountUpdated = function () {
      AccountService.getRegionsForAccount($scope.securityGroup.credentials).then(function (regions) {
        $scope.regions = regions;
        $scope.securityGroup.regions = regions;
        ctrl.updateName();
      });
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

    $scope.securityGroup = securityGroup;

    $scope.state = {
      refreshingSecurityGroups: false,
    };

    $scope.taskMonitor = new TaskMonitor({
      application: application,
      title: `Updating your ${FirewallLabels.get('firewall')}`,
      modalInstance: $uibModalInstance,
      onTaskComplete: onTaskComplete,
    });

    AccountService.listAccounts('azure').then(function (accounts) {
      $scope.accounts = accounts;
      ctrl.accountUpdated();
    });

    ctrl.addRule = function (ruleset) {
      ruleset.push({
        name: $scope.securityGroup.name + '-Rule' + ruleset.length,
        priority: ruleset.length === 0 ? 100 : 100 * (ruleset.length + 1),
        protocol: 'tcp',
        access: 'Allow',
        direction: 'InBound',
        sourceAddressPrefix: '*',
        sourcePortRange: '*',
        destinationAddressPrefix: '*',
        destinationPortRange: '7001-7001',
        startPort: 7001,
        endPort: 7001,
      });
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
        region: $scope.securityGroup.region,
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

    ctrl.portUpdated = function (ruleset, index) {
      ruleset[index].destinationPortRange = ruleset[index].startPort + '-' + ruleset[index].endPort;
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

    ctrl.upsert = function () {
      $scope.taskMonitor.submit(function () {
        const params = {
          cloudProvider: 'azure',
          appName: application.name,
          region: $scope.securityGroup.region,
          subnet: 'none',
          vpcId: 'null',
        };
        $scope.securityGroup.type = 'upsertSecurityGroup';

        return azureSecurityGroupWriter.upsertSecurityGroup($scope.securityGroup, application, 'Clone', params);
      });
    };
  },
]);
