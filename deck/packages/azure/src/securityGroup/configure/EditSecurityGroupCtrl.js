'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { module } from 'angular';
import _ from 'lodash';

import {
  CACHE_INITIALIZER_SERVICE,
  FirewallLabels,
  InfrastructureCaches,
  SECURITY_GROUP_READER,
  TaskMonitor,
} from '@spinnaker/core';

import { AZURE_SECURITYGROUP_SECURITYGROUP_WRITE_SERVICE } from '../securityGroup.write.service';

export const AZURE_SECURITYGROUP_CONFIGURE_EDITSECURITYGROUPCTRL =
  'spinnaker.azure.securityGroup.azure.edit.controller';
export const name = AZURE_SECURITYGROUP_CONFIGURE_EDITSECURITYGROUPCTRL; // for backwards compatibility
module(AZURE_SECURITYGROUP_CONFIGURE_EDITSECURITYGROUPCTRL, [
  UIROUTER_ANGULARJS,
  CACHE_INITIALIZER_SERVICE,
  SECURITY_GROUP_READER,
  AZURE_SECURITYGROUP_SECURITYGROUP_WRITE_SERVICE,
]).controller('azureEditSecurityGroupCtrl', [
  '$scope',
  '$uibModalInstance',
  '$exceptionHandler',
  '$state',
  'securityGroupReader',
  'cacheInitializer',
  'application',
  'securityGroup',
  'azureSecurityGroupWriter',
  function (
    $scope,
    $uibModalInstance,
    $exceptionHandler,
    $state,
    securityGroupReader,
    cacheInitializer,
    application,
    securityGroup,
    azureSecurityGroupWriter,
  ) {
    $scope.pages = {
      ingress: require('./createSecurityGroupIngress.html'),
    };

    securityGroup.securityRules = _.map(securityGroup.securityRules, function (rule) {
      if (!_.isEmpty(rule.protocol)) {
        rule.protocolUI = rule.protocol.toLowerCase();
      }

      rule.destPortRanges = rule.destinationPortRangeModel;
      rule.sourceIPCIDRRanges = rule.sourceAddressPrefixModel;

      return rule;
    });

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

    this.getSecurityGroupRefreshTime = function () {
      return InfrastructureCaches.get('securityGroups').getStats().ageMax;
    };

    this.refreshSecurityGroups = function () {
      $scope.state.refreshingSecurityGroups = true;
      return cacheInitializer.refreshCache('securityGroups').then(function () {
        initializeSecurityGroups().then(function () {
          $scope.state.refreshingSecurityGroups = false;
        });
      });
    };

    function initializeSecurityGroups() {
      return securityGroupReader.getAllSecurityGroups().then(function (securityGroups) {
        const account = securityGroup.accountName;
        const region = securityGroup.region;
        const availableGroups = _.filter(securityGroups[account].azure[region], {
          /*vpcId: vpcId*/
        });
        $scope.availableSecurityGroups = _.map(availableGroups, 'name');
      });
    }

    this.addRule = function (ruleset) {
      ruleset.push({
        name: $scope.securityGroup.name + '-Rule' + ruleset.length,
        priority: ruleset.length === 0 ? 100 : 100 * (ruleset.length + 1),
        protocolUI: 'tcp',
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

    this.portUpdated = function (ruleset, index) {
      if (!_.isEmpty(ruleset[index].sourceIPCIDRRanges)) {
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

    this.sourceIPCIDRUpdated = function (ruleset, index) {
      if (!_.isEmpty(ruleset[index].sourceIPCIDRRanges)) {
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

    this.removeRule = function (ruleset, index) {
      ruleset.splice(index, 1);
    };

    this.moveUp = function (ruleset, index) {
      if (index === 0) return;
      swapRules(ruleset, index, index - 1);
    };
    this.moveDown = function (ruleset, index) {
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

    $scope.taskMonitor.onTaskComplete = $uibModalInstance.dismiss;

    this.upsert = function () {
      $scope.taskMonitor.submit(function () {
        const params = {
          cloudProvider: 'azure',
          appName: application.name,
          region: $scope.securityGroup.region,
          subnet: null,
          vpcId: 'null',
        };
        $scope.securityGroup.type = 'upsertSecurityGroup';

        return azureSecurityGroupWriter.upsertSecurityGroup($scope.securityGroup, application, 'Update', params);
      });
    };

    this.cancel = function () {
      $uibModalInstance.dismiss();
    };
  },
]);
