'use strict';

import _ from 'lodash';

var angular = require('angular');

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {SECURITY_GROUP_READER} from 'core/securityGroup/securityGroupReader.service';
import {SECURITY_GROUP_WRITER} from 'core/securityGroup/securityGroupWriter.service';
import {TASK_MONITOR_BUILDER} from 'core/task/monitor/taskMonitor.builder';

module.exports = angular
  .module('spinnaker.azure.securityGroup.baseConfig.controller', [
    require('angular-ui-router').default,
    TASK_MONITOR_BUILDER,
    SECURITY_GROUP_READER,
    SECURITY_GROUP_WRITER,
    ACCOUNT_SERVICE,
    require('core/modal/wizard/modalWizard.service.js'),
  ])
  .controller('azureConfigSecurityGroupMixin', function ($scope,
                                                         $state,
                                                         $uibModalInstance,
                                                         taskMonitorBuilder,
                                                         application,
                                                         securityGroup,
                                                         securityGroupReader,
                                                         securityGroupWriter,
                                                         accountService,
                                                         modalWizardService,
                                                         cacheInitializer) {



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
        region: $scope.securityGroup.regions[0],
        vpcId: $scope.securityGroup.vpcId,
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

    function clearSecurityGroups() {
      $scope.availableSecurityGroups = [];
      $scope.existingSecurityGroupNames = [];
    }

    ctrl.accountUpdated = function() {
      var account = $scope.securityGroup.credentials || $scope.securityGroup.accountName;
      accountService.getRegionsForAccount(account).then(function(regions) {
        $scope.regions = _.map(regions, 'name');
        clearSecurityGroups();
        ctrl.regionUpdated();
        ctrl.updateName();
      });
    };

    //ctrl.ifVcsFoundInAllRegions = function() {
    //  var foundInAllRegions = true;
    //  _.forEach($scope.securityGroup.regions, function(region) {
    //    if (!_.some(vpcsToTest, { region: region, account: $scope.securityGroup.credentials })) {
    //      foundInAllRegions = false;
    //    }
    //  });
    //  return foundInAllRegions;
    //};

    ctrl.regionUpdated = function() {
    };

    this.vpcUpdated = function() {
      var account = $scope.securityGroup.credentials || $scope.securityGroup.accountName,
        regions = $scope.securityGroup.regions;
      if (account && regions && regions.length) {
        configureFilteredSecurityGroups();
      } else {
        clearSecurityGroups();
      }
    };

    function configureFilteredSecurityGroups() {
      var vpcId = $scope.securityGroup.vpcId || null;
      var account = $scope.securityGroup.credentials || $scope.securityGroup.accountName;
      var regions = $scope.securityGroup.regions || [];
      var existingSecurityGroupNames = [];
      var availableSecurityGroups = [];

      regions.forEach(function (region) {
        var regionalVpcId = null;
        if (vpcId) {
          var baseVpc = _.find($scope.allVpcs, { id: vpcId });
          regionalVpcId = _.find($scope.allVpcs, { account: account, region: region, name: baseVpc.name }).id;
        }

        var regionalSecurityGroups = _.filter(allSecurityGroups[account].azure[region], { vpcId: regionalVpcId }),
          regionalGroupNames = _.map(regionalSecurityGroups, 'name');

        existingSecurityGroupNames = _.uniq(existingSecurityGroupNames.concat(regionalGroupNames));

        if (!availableSecurityGroups.length) {
          availableSecurityGroups = existingSecurityGroupNames;
        } else {
          availableSecurityGroups = _.intersection(availableSecurityGroups, regionalGroupNames);
        }
      });

      $scope.availableSecurityGroups = availableSecurityGroups;
      $scope.existingSecurityGroupNames = existingSecurityGroupNames;
      clearInvalidSecurityGroups();
    }

    ctrl.mixinUpsert = function (descriptor) {
      $scope.taskMonitor.submit(
        function() {
          return securityGroupWriter.upsertSecurityGroup($scope.securityGroup, application, descriptor);
        }
      );
    };

    function clearInvalidSecurityGroups() {
      var removed = $scope.state.removedRules;
      $scope.securityGroup.securityGroupIngress = $scope.securityGroup.securityGroupIngress.filter(function(rule) {
        if (rule.name && !$scope.availableSecurityGroups.includes(rule.name) && !removed.includes(rule.name)) {
          removed.push(rule.name);
          return false;
        }
        return true;
      });
      if (removed.length) {
        modalWizardService.getWizard().markDirty('Ingress');
      }
    }

    ctrl.refreshSecurityGroups = function() {
      $scope.state.refreshingSecurityGroups = true;
      return cacheInitializer.refreshCache('securityGroups').then(function() {
        return ctrl.initializeSecurityGroups().then(function() {
          ctrl.vpcUpdated();
          $scope.state.refreshingSecurityGroups = false;
        });
      });
    };

    var allSecurityGroups = {};

    ctrl.initializeSecurityGroups = function() {
      return securityGroupReader.getAllSecurityGroups().then(function (securityGroups) {
        allSecurityGroups = securityGroups;
        var account = $scope.securityGroup.credentials || $scope.securityGroup.accountName;
        var region = $scope.securityGroup.regions[0];
        var vpcId = $scope.securityGroup.vpcId || null;

        var availableGroups;
        if(account && region) {
          availableGroups = _.filter(securityGroups[account].azure[region], { vpcId: vpcId });
        } else {
          availableGroups = securityGroups;
        }

        $scope.availableSecurityGroups = _.map(availableGroups, 'name');
      });
    };

    ctrl.cancel = function () {
      $uibModalInstance.dismiss();
    };

    ctrl.getCurrentNamePattern = function() {
      return $scope.securityGroup.vpcId ? vpcPattern : classicPattern;
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

    ctrl.namePattern = {
      test: function(name) {
        return ctrl.getCurrentNamePattern().test(name);
      }
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
       modalWizardService.getWizard().markClean('Ingress');
       modalWizardService.getWizard().markComplete('Ingress');
    };

    var classicPattern = /^[\x00-\x7F]+$/;
    var vpcPattern = /^[a-zA-Z0-9\s._\-:\/()#,@[\]+=&;{}!$*]+$/;


  });

