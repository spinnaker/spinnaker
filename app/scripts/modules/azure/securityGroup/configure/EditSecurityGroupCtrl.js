'use strict';

const angular = require('angular');

import {
  CACHE_INITIALIZER_SERVICE,
  FirewallLabels,
  InfrastructureCaches,
  SECURITY_GROUP_READER,
  TaskMonitor,
} from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.azure.securityGroup.azure.edit.controller', [
    require('@uirouter/angularjs').default,
    CACHE_INITIALIZER_SERVICE,
    SECURITY_GROUP_READER,
    require('../securityGroup.write.service').name,
  ])
  .controller('azureEditSecurityGroupCtrl', function(
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

    securityGroup.securityRules = _.map(securityGroup.securityRules, function(rule) {
      var temp = rule.destinationPortRange.split('-');
      rule.startPort = Number(temp[0]);
      rule.endPort = Number(temp[1]);
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

    this.getSecurityGroupRefreshTime = function() {
      return InfrastructureCaches.get('securityGroups').getStats().ageMax;
    };

    this.refreshSecurityGroups = function() {
      $scope.state.refreshingSecurityGroups = true;
      return cacheInitializer.refreshCache('securityGroups').then(function() {
        initializeSecurityGroups().then(function() {
          $scope.state.refreshingSecurityGroups = false;
        });
      });
    };

    function initializeSecurityGroups() {
      return securityGroupReader.getAllSecurityGroups().then(function(securityGroups) {
        var account = securityGroup.accountName,
          region = securityGroup.region,
          availableGroups = _.filter(securityGroups[account].azure[region], {
            /*vpcId: vpcId*/
          });
        $scope.availableSecurityGroups = _.map(availableGroups, 'name');
      });
    }

    this.addRule = function(ruleset) {
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
      var newStateParams = {
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

    this.portUpdated = function(ruleset, index) {
      ruleset[index].destinationPortRange = ruleset[index].startPort + '-' + ruleset[index].endPort;
    };

    this.removeRule = function(ruleset, index) {
      ruleset.splice(index, 1);
    };

    this.moveUp = function(ruleset, index) {
      if (index === 0) return;
      swapRules(ruleset, index, index - 1);
    };
    this.moveDown = function(ruleset, index) {
      if (index === ruleset.length - 1) return;
      swapRules(ruleset, index, index + 1);
    };

    function swapRules(ruleset, a, b) {
      var temp, priorityA, priorityB;
      temp = ruleset[b];
      priorityA = ruleset[a].priority;
      priorityB = ruleset[b].priority;
      //swap elements
      ruleset[b] = ruleset[a];
      ruleset[a] = temp;
      //swap priorities
      ruleset[a].priority = priorityA;
      ruleset[b].priority = priorityB;
    }

    $scope.taskMonitor.onTaskComplete = $uibModalInstance.dismiss;

    this.upsert = function() {
      $scope.taskMonitor.submit(function() {
        let params = {
          cloudProvider: 'azure',
          appName: application.name,
          securityGroupName: $scope.securityGroup.name,
          region: $scope.securityGroup.region,
          subnet: 'none',
          vpcId: 'null',
        };
        $scope.securityGroup.type = 'upsertSecurityGroup';

        return azureSecurityGroupWriter.upsertSecurityGroup($scope.securityGroup, application, 'Update', params);
      });
    };

    this.cancel = function() {
      $uibModalInstance.dismiss();
    };
  });
