'use strict';

const angular = require('angular');

import {
  AccountService,
  FirewallLabels,
  LOAD_BALANCER_READ_SERVICE,
  SECURITY_GROUP_READER,
  SecurityGroupWriter,
  TaskMonitor,
} from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.securityGroup.kubernetes.create.controller', [
    require('@uirouter/angularjs').default,
    LOAD_BALANCER_READ_SERVICE,
    SECURITY_GROUP_READER,
    require('../../../namespace/selectField.directive').name,
    require('../../transformer').name,
  ])
  .controller('kubernetesUpsertSecurityGroupController', ['$q', '$scope', '$uibModalInstance', '$state', 'application', 'securityGroup', 'kubernetesSecurityGroupTransformer', 'securityGroupReader', 'loadBalancerReader', function(
    $q,
    $scope,
    $uibModalInstance,
    $state,
    application,
    securityGroup,
    kubernetesSecurityGroupTransformer,
    securityGroupReader,
    loadBalancerReader,
  ) {
    var ctrl = this;
    $scope.firewallLabel = FirewallLabels.get('Firewall');
    $scope.firewallLabelLc = FirewallLabels.get('firewall');

    $scope.isNew = !securityGroup.edit;
    $scope.securityGroup = securityGroup;

    $scope.pages = {
      basicSettings: require('./basicSettings.html'),
      backend: require('./backend.html'),
      rules: require('./rules.html'),
      tls: require('./tls.html'),
      advancedSettings: require('./advancedSettings.html'),
    };

    $scope.state = {
      accountsLoaded: false,
      securityGroupNamesLoaded: false,
      submitting: false,
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
      title: `${$scope.isNew ? 'Creating ' : 'Updating '} your ${FirewallLabels.get('firewall')}`,
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

    function initializeEditMode() {
      $scope.state.accountsLoaded = true;
      return loadBalancerReader
        .listLoadBalancers('kubernetes')
        .then(loadBalancers => ($scope.loadBalancers = getLoadBalancerNames(loadBalancers)));
    }

    function initializeCreateMode() {
      return $q
        .all({
          accounts: AccountService.listAccounts('kubernetes'),
          loadBalancers: loadBalancerReader.listLoadBalancers('kubernetes'),
        })
        .then(function(backingData) {
          $scope.accounts = backingData.accounts;
          $scope.state.accountsLoaded = true;

          var accountNames = _.map($scope.accounts, 'name');
          if (accountNames.length && !accountNames.includes($scope.securityGroup.account)) {
            $scope.securityGroup.account = accountNames[0];
          }

          ctrl.accountUpdated();
        });
    }

    function initializeSecurityGroupNames() {
      securityGroupReader.loadSecurityGroups('kubernetes').then(function(securityGroups) {
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
      initializeCreateMode();
    } else {
      initializeEditMode();
    }
    initializeSecurityGroupNames();
    // Controller API
    this.updateName = function() {
      $scope.securityGroup.name = this.getName();
      $scope.securityGroup.securityGroupName = this.getName();
    };

    this.getName = function() {
      var securityGroup = $scope.securityGroup;
      var securityGroupName = [application.name, securityGroup.stack || '', securityGroup.detail || ''].join('-');
      return _.trimEnd(securityGroupName, '-');
    };

    this.accountUpdated = function() {
      AccountService.getAccountDetails($scope.securityGroup.account).then(function(accountDetails) {
        $scope.namespaces = accountDetails.namespaces;
        ctrl.namespaceUpdated();
      });
    };

    this.namespaceUpdated = function() {
      updateSecurityGroupNames();
      loadBalancerReader
        .listLoadBalancers('kubernetes')
        .then(loadBalancers => ($scope.loadBalancers = getLoadBalancerNames(loadBalancers)));
      ctrl.updateName();
    };

    this.submit = function() {
      var descriptor = $scope.isNew ? 'Create' : 'Update';

      this.updateName();
      $scope.taskMonitor.submit(function() {
        let params = {
          cloudProvider: 'kubernetes',
          region: $scope.securityGroup.namespace,
        };

        // Change TLS hosts from string to array for Clouddriver (if it isn't already an array)
        for (let idx in $scope.securityGroup.tls) {
          if (!Array.isArray($scope.securityGroup.tls[idx].hosts)) {
            $scope.securityGroup.tls[idx].hosts = [$scope.securityGroup.tls[idx].hosts];
          }
        }

        return SecurityGroupWriter.upsertSecurityGroup($scope.securityGroup, application, descriptor, params);
      });
    };

    this.cancel = function() {
      $uibModalInstance.dismiss();
    };
  }]);
