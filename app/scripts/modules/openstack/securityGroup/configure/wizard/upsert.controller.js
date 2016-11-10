'use strict';

import modalWizardServiceModule from 'core/modal/wizard/v2modalWizard.service';

let angular = require('angular');
import {ACCOUNT_SERVICE} from 'core/account/account.service';

module.exports = angular.module('spinnaker.securityGroup.openstack.create.controller', [
  require('angular-ui-router'),
  require('core/securityGroup/securityGroup.write.service.js'),
  require('core/securityGroup/securityGroup.read.service.js'),
  require('core/loadBalancer/loadBalancer.read.service.js'),
  ACCOUNT_SERVICE,
  modalWizardServiceModule,
  require('core/task/monitor/taskMonitorService.js'),
  require('core/search/search.service.js'),
  require('../../../region/regionSelectField.directive.js'),
  require('../../transformer.js'),
])
  .controller('openstackUpsertSecurityGroupController', function($q, $scope, $uibModalInstance, $state,
                                                                 application, securityGroup,
                                                                 accountService, openstackSecurityGroupTransformer, securityGroupReader, loadBalancerReader,
                                                                 searchService, v2modalWizardService, securityGroupWriter, taskMonitorService, namingService) {
    var ctrl = this;
    $scope.isNew = !securityGroup.edit;
    $scope.securityGroup = securityGroup;

    $scope.pages = {
      basicSettings: require('./basicSettings.html'),
      rules: require('./rules.html'),
    };

    $scope.state = {
      accountsLoaded: false,
      securityGroupNamesLoaded: false,
      submitting: false
    };

    function onApplicationRefresh() {
      // If the user has already closed the modal, do not navigate to the new details view
      if ($scope.$$destroyed) {
        return;
      }
      $uibModalInstance.close();
      var newStateParams = {
        name: $scope.securityGroup.name,
        accountId: $scope.securityGroup.account,
        namespace: $scope.securityGroup.namespace,
        provider: 'openstack',
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

    $scope.taskMonitor = taskMonitorService.buildTaskMonitor({
      application: application,
      title: ($scope.isNew ? 'Creating ' : 'Updating ') + 'your security group',
      modalInstance: $uibModalInstance,
      onTaskComplete: onTaskComplete,
    });

    var allSecurityGroupNames = {};

    function getLoadBalancerNames(loadBalancers) {
      return _.chain(loadBalancers)
        .filter({ account: $scope.securityGroup.account, region: $scope.securityGroup.region })
        .map('name')
        .flattenDeep()
        .uniq()
        .value();
    }

    function initializeCreateMode() {
      return $q.all({
        accounts: accountService.listAccounts('openstack'),
      }).then(function(backingData) {
        $scope.accounts = backingData.accounts;
        var accountNames = _.map($scope.accounts, 'name');
        if (accountNames.length && !accountNames.includes($scope.securityGroup.account)) {
          $scope.securityGroup.account = accountNames[0];
        }

        $scope.loadBalancers = getLoadBalancerNames(backingData.loadBalancers);

        ctrl.accountUpdated();
      });
    }

    function initializeSecurityGroupNames() {
      securityGroupReader.loadSecurityGroups('openstack').then(function (securityGroups) {
        for (var account in securityGroups) {
          if (!allSecurityGroupNames[account]) {
            allSecurityGroupNames[account] = {};
          }

          let securityGroupsByAccount = securityGroups[account];
          for (var namespace in securityGroupsByAccount) {
            if (!allSecurityGroupNames[account][namespace]) {
              allSecurityGroupNames[account][namespace] = [];
            }

            let securityGroupsByNamespace = securityGroupsByAccount[namespace];
            for (var found in securityGroupsByNamespace) {
              allSecurityGroupNames[account][namespace].push(found);
            }
          }
        }

        updateSecurityGroupNames();
        $scope.state.securityGroupNamesLoaded = true;
      });
    }

    function updateSecurityGroupNames() {
      var account = $scope.securityGroup.account;

      if (allSecurityGroupNames[account]) {
        $scope.existingSecurityGroupNames = _.flatten(_.map(allSecurityGroupNames[account]));
      } else {
        $scope.existingSecurityGroupNames = [];
      }
    }

    if ($scope.isNew) {
        $scope.securityGroup = openstackSecurityGroupTransformer.constructNewSecurityGroupTemplate();
        initializeCreateMode();
        $scope.state.accountsLoaded = true;
    }
    else {
      $scope.securityGroup = openstackSecurityGroupTransformer.prepareForEdit(securityGroup);

      var result = namingService.parseServerGroupName($scope.securityGroup.name);
      $scope.securityGroup.stack = result.stack;
      $scope.securityGroup.detail = result.freeFormDetails;
      $scope.state.accountsLoaded = true;
    }

    initializeSecurityGroupNames();

    // Controller API
    this.updateName = function() {
      $scope.securityGroup.name = this.getName();
      $scope.securityGroup.securityGroupName = this.getName();
    };

    this.getName = function() {
      var securityGroup = $scope.securityGroup;
      var securityGroupName = _.compact([application.name, (securityGroup.stack), (securityGroup.detail)]).join('-');
      return _.trimEnd(securityGroupName, '-');
    };

    this.accountUpdated = function() {
    };
    this.onRegionChanged = function(region) {
      $scope.securityGroup.region = region;
    };

    this.submit = function () {
      var descriptor = $scope.isNew ? 'Create' : 'Update';

      this.updateName();
      $scope.taskMonitor.submit(
        function() {
          let params = {
            cloudProvider: 'openstack',
          };

          var copyOfSG = angular.copy($scope.securityGroup);
          copyOfSG = openstackSecurityGroupTransformer.prepareForSaving(copyOfSG);

          return securityGroupWriter.upsertSecurityGroup(copyOfSG, application, descriptor, params);
        }
      );
    };

    this.cancel = function () {
      $uibModalInstance.dismiss();
    };
  });
