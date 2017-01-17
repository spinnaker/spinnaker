'use strict';

import _ from 'lodash';

var angular = require('angular');

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {TASK_MONITOR_BUILDER} from 'core/task/monitor/taskMonitor.builder';

module.exports = angular
  .module('spinnaker.azure.securityGroup.clone.controller', [
    ACCOUNT_SERVICE,
    TASK_MONITOR_BUILDER,
    require('../securityGroup.write.service.js'),
    require('../configure/CreateSecurityGroupCtrl.js')
  ])
  .controller('azureCloneSecurityGroupController', function($scope, $uibModalInstance, $controller, $state, taskMonitorBuilder, accountService,
                                                            azureSecurityGroupWriter, securityGroup, application) {
    var ctrl = this;

    $scope.pages = {
      location: require('../configure/createSecurityGroupProperties.html'),
      ingress: require('../configure/createSecurityGroupIngress.html'),
    };

    securityGroup.securityRules = _.map(securityGroup.securityRules,function(rule) {
      var temp = rule.destinationPortRange.split('-');
      rule.startPort = Number(temp[0]);
      rule.endPort = Number(temp[1]);
      return rule;
    });

    ctrl.accountUpdated = function() {
      accountService.getRegionsForAccount($scope.securityGroup.credentials).then(function(regions) {
        $scope.regions = regions;
        $scope.securityGroup.regions = regions;
        ctrl.updateName();
      });
    };

    ctrl.cancel = function () {
      $uibModalInstance.dismiss();
    };

    ctrl.updateName = function() {
      var securityGroup = $scope.securityGroup,
        name = application.name;
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

    $scope.taskMonitor = taskMonitorBuilder.buildTaskMonitor({
      application: application,
      title: 'Updating your security group',
      modalInstance: $uibModalInstance,
      onTaskComplete: onTaskComplete,
    });

    accountService.listAccounts('azure').then(function(accounts) {
      $scope.accounts = accounts;
      ctrl.accountUpdated();
    });

    ctrl.addRule = function(ruleset) {
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
        endPort: 7001
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
      if (!$state.includes('**.securityGroupDetails')) {
        $state.go('.securityGroupDetails', newStateParams);
      } else {
        $state.go('^.securityGroupDetails', newStateParams);
      }
    }

    function onTaskComplete() {
      application.securityGroups.refresh();
      application.securityGroups.onNextRefresh($scope, onApplicationRefresh);
    }

    ctrl.portUpdated = function(ruleset, index)
    {
        ruleset[index].destinationPortRange =
            ruleset[index].startPort + '-' + ruleset[index].endPort;
    };
    ctrl.removeRule = function(ruleset, index) {
      ruleset.splice(index, 1);
    };
    ctrl.moveUp = function(ruleset, index) {
      if(index === 0)
        return;
      swapRules(ruleset, index, index - 1);
    };
    ctrl.moveDown = function(ruleset, index) {
      if(index === ruleset.length - 1)
        return;
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

    ctrl.upsert = function () {
      $scope.taskMonitor.submit(
        function() {
          let params = {
            cloudProvider: 'azure',
            appName: application.name,
            securityGroupName: $scope.securityGroup.name,
            region: $scope.securityGroup.region,
            subnet : 'none',
            vpcId: 'null'
            };
          $scope.securityGroup.type = 'upsertSecurityGroup';

          return azureSecurityGroupWriter.upsertSecurityGroup($scope.securityGroup, application, 'Clone', params);
        }
      );
    };
  });
