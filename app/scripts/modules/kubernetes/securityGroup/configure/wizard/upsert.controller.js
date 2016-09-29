'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.securityGroup.kubernetes.create.controller', [
  require('angular-ui-router'),
  require('../../../../core/securityGroup/securityGroup.write.service.js'),
  require('../../../../core/securityGroup/securityGroup.read.service.js'),
  require('../../../../core/loadBalancer/loadBalancer.read.service.js'),
  require('../../../../core/account/account.service.js'),
  require('../../../../core/modal/wizard/v2modalWizard.service.js'),
  require('../../../../core/task/monitor/taskMonitorService.js'),
  require('../../../../core/search/search.service.js'),
  require('../../../namespace/selectField.directive.js'),
  require('../../transformer.js'),
])
  .controller('kubernetesUpsertSecurityGroupController', function($q, $scope, $uibModalInstance, $state,
                                                                 application, securityGroup,
                                                                 accountService, kubernetesSecurityGroupTransformer, securityGroupReader, loadBalancerReader,
                                                                 searchService, v2modalWizardService, securityGroupWriter, taskMonitorService) {
    var ctrl = this;
    $scope.isNew = !securityGroup.edit;
    $scope.securityGroup = securityGroup;

    $scope.pages = {
      basicSettings: require('./basicSettings.html'),
      backend: require('./backend.html'),
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
        provider: 'kubernetes',
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
        .filter({ account: $scope.securityGroup.account })
        .filter({ namespace: $scope.securityGroup.namespace })
        .map('name')
        .flattenDeep()
        .uniq()
        .value();
    }

    function initializeCreateMode() {
      return $q.all({
        accounts: accountService.listAccounts('kubernetes'),
        loadBalancers: loadBalancerReader.listLoadBalancers('kubernetes'),
      }).then(function(backingData) {
        $scope.accounts = backingData.accounts;
        $scope.state.accountsLoaded = true;

        var accountNames = _.map($scope.accounts, 'name');
        if (accountNames.length && accountNames.indexOf($scope.securityGroup.account) === -1) {
          $scope.securityGroup.account = accountNames[0];
        }

        $scope.loadBalancers = getLoadBalancerNames(backingData.loadBalancers);

        ctrl.accountUpdated();
      });
    }

    function initializeSecurityGroupNames() {
      securityGroupReader.loadSecurityGroups('kubernetes').then(function (securityGroups) {
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
      $scope.securityGroup = kubernetesSecurityGroupTransformer.constructNewSecurityGroupTemplate();
    }

    initializeSecurityGroupNames();
    initializeCreateMode();

    // Controller API
    this.updateName = function() {
      $scope.securityGroup.name = this.getName();
      $scope.securityGroup.securityGroupName = this.getName();
    };

    this.getName = function() {
      var securityGroup = $scope.securityGroup;
      var securityGroupName = [application.name, (securityGroup.stack || ''), (securityGroup.detail || '')].join('-');
      return _.trimEnd(securityGroupName, '-');
    };

    this.accountUpdated = function() {
      accountService.getAccountDetails($scope.securityGroup.account).then(function(details) {
        $scope.namespaces = details.namespaces;
        ctrl.namespaceUpdated();
      });
    };

    this.namespaceUpdated = function() {
      updateSecurityGroupNames();
      ctrl.updateName();
    };

    this.submit = function () {
      var descriptor = $scope.isNew ? 'Create' : 'Update';

      this.updateName();
      $scope.taskMonitor.submit(
        function() {
          let params = {
            cloudProvider: 'kubernetes',
            region: $scope.securityGroup.namespace,
          };
          return securityGroupWriter.upsertSecurityGroup($scope.securityGroup, application, descriptor, params);
        }
      );
    };

    this.cancel = function () {
      $uibModalInstance.dismiss();
    };
  });
