'use strict';

import _ from 'lodash';
var angular = require('angular');

import {V2_MODAL_WIZARD_SERVICE} from 'core/modal/wizard/v2modalWizard.service';
import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {NETWORK_READ_SERVICE} from 'core/network/network.read.service';
import {SECURITY_GROUP_READER} from 'core/securityGroup/securityGroupReader.service';
import {SECURITY_GROUP_WRITER} from 'core/securityGroup/securityGroupWriter.service';
import {TASK_MONITOR_BUILDER} from 'core/task/monitor/taskMonitor.builder';

module.exports = angular
  .module('spinnaker.google.securityGroup.baseConfig.controller', [
    require('angular-ui-router'),
    TASK_MONITOR_BUILDER,
    ACCOUNT_SERVICE,
    NETWORK_READ_SERVICE,
    V2_MODAL_WIZARD_SERVICE,
    SECURITY_GROUP_READER,
    SECURITY_GROUP_WRITER
  ])
  .controller('gceConfigSecurityGroupMixin', function ($scope,
                                                       $state,
                                                       $uibModalInstance,
                                                       taskMonitorBuilder,
                                                       application,
                                                       securityGroup,
                                                       securityGroupReader,
                                                       securityGroupWriter,
                                                       accountService,
                                                       v2modalWizardService,
                                                       cacheInitializer,
                                                       networkReader) {



    var ctrl = this;

    $scope.isNew = true;

    $scope.state = {
      submitting: false,
      refreshingSecurityGroups: false,
      removedRules: [],
      infiniteScroll: {
        numToAdd: 20,
        currentItems: 20,
      },
    };

    $scope.wizard = v2modalWizardService;

    ctrl.addMoreItems = function() {
      $scope.state.infiniteScroll.currentItems += $scope.state.infiniteScroll.numToAdd;
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
        region: 'global',
        vpcId: $scope.securityGroup.vpcId,
        provider: 'gce',
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

    $scope.taskMonitor = taskMonitorBuilder.buildTaskMonitor({
      application: application,
      title: 'Creating your security group',
      modalInstance: $uibModalInstance,
      onTaskComplete: onTaskComplete,
    });

    $scope.securityGroup = securityGroup;

    ctrl.upsert = function () {
      $scope.taskMonitor.submit(
        function() {
          return securityGroupWriter.upsertSecurityGroup($scope.securityGroup, application, 'Create');
        }
      );
    };

    ctrl.mixinUpsert = function (descriptor) {
      $scope.taskMonitor.submit(
        function() {
          var allowed = _.map($scope.securityGroup.ipIngress, function(ipIngressRule) {
            var rule = {
              ipProtocol: ipIngressRule.type,
            };

            if (ipIngressRule.startPort && ipIngressRule.endPort) {
              rule.portRanges = [ipIngressRule.startPort + '-' + ipIngressRule.endPort];
            }

            return rule;
          });

          return securityGroupWriter.upsertSecurityGroup($scope.securityGroup, application, descriptor, {
            cloudProvider: 'gce',
            securityGroupName: $scope.securityGroup.name,
            sourceRanges: _.uniq(_.map($scope.securityGroup.sourceRanges, 'value')),
            allowed: allowed,
            region: 'global',
            network: $scope.securityGroup.network,
          });
        }
      );
    };

    ctrl.accountUpdated = function() {
      ctrl.initializeSecurityGroups();
      ctrl.updateNetworks();
      ctrl.updateName();
    };

    ctrl.refreshSecurityGroups = function() {
      $scope.state.refreshingSecurityGroups = true;
      return cacheInitializer.refreshCache('securityGroups').then(function() {
        return ctrl.initializeSecurityGroups().then(function() {
          $scope.state.refreshingSecurityGroups = false;
        });
      });
    };

    ctrl.initializeSecurityGroups = function() {
      return securityGroupReader.getAllSecurityGroups().then(function (securityGroups) {
        var account = $scope.securityGroup.credentials || $scope.securityGroup.accountName;

        var existingGroups;
        if(account) {
          existingGroups = securityGroups[account].gce.global;
        } else {
          existingGroups = securityGroups;
        }

        $scope.existingSecurityGroupNames = _.map(existingGroups, 'name');
      });
    };

    ctrl.cancel = function() {
      $uibModalInstance.dismiss();
    };

    ctrl.updateNetworks = function() {
      networkReader.listNetworksByProvider('gce').then(function(gceNetworks) {
        var account = $scope.securityGroup.credentials || $scope.securityGroup.accountName;
        $scope.securityGroup.backingData.networks = _.map(_.filter(gceNetworks, { account: account }), 'name');
      });
    };

    ctrl.getCurrentNamePattern = function() {
      return /^[a-zA-Z0-9-]*$/;
    };

    ctrl.updateName = function() {
      var securityGroup = $scope.securityGroup,
        name = application.name;
      if (securityGroup.detail) {
        name += '-' + securityGroup.detail;
        name = _.trimEnd(name, '-');
      }
      securityGroup.name = name;
      $scope.namePreview = name;
    };

    ctrl.namePattern = {
      test: function(name) {
        return ctrl.getCurrentNamePattern().test(name);
      }
    };

    ctrl.addSourceCIDR = function(sourceRanges) {
      sourceRanges.push({value: '0.0.0.0/0'});
    };

    ctrl.removeSourceCIDR = function(sourceRanges, index) {
      sourceRanges.splice(index, 1);
    };

    ctrl.addRule = function(ruleset) {
      ruleset.push({
        type: 'tcp',
        startPort: 7001,
        endPort: 7001,
      });
    };

    ctrl.removeRule = function(ruleset, index) {
      ruleset.splice(index, 1);
    };

    ctrl.dismissRemovedRules = function() {
      $scope.state.removedRules = [];
      v2modalWizardService.markClean('Ingress');
      v2modalWizardService.markComplete('Ingress');
    };

  });

