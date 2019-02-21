'use strict';

const angular = require('angular');

import { AccountService, LOAD_BALANCER_READ_SERVICE, LoadBalancerWriter, TaskMonitor } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.dcos.loadBalancer.create.controller', [
    LOAD_BALANCER_READ_SERVICE,
    require('../../transformer').name,
  ])
  .controller('dcosUpsertLoadBalancerController', ['$scope', '$uibModalInstance', '$state', 'application', 'loadBalancer', 'isNew', 'loadBalancerReader', 'dcosLoadBalancerTransformer', function(
    $scope,
    $uibModalInstance,
    $state,
    application,
    loadBalancer,
    isNew,
    loadBalancerReader,
    dcosLoadBalancerTransformer,
  ) {
    var ctrl = this;
    $scope.isNew = isNew;

    $scope.pages = {
      basicSettings: require('./basicSettings.html'),
      resources: require('./resources.html'),
      ports: require('./ports.html'),
    };

    $scope.state = {
      accountsLoaded: false,
      submitting: false,
    };

    function onApplicationRefresh() {
      // If the user has already closed the modal, do not navigate to the new details view
      if ($scope.$$destroyed) {
        return;
      }
      $uibModalInstance.close();
      var newStateParams = {
        name: $scope.loadBalancer.name,
        accountId: $scope.loadBalancer.account,
        region: $scope.loadBalancer.region,
        provider: 'dcos',
      };
      if (!$state.includes('**.loadBalancerDetails')) {
        $state.go('.loadBalancerDetails', newStateParams);
      } else {
        $state.go('^.loadBalancerDetails', newStateParams);
      }
    }

    function onTaskComplete() {
      application.loadBalancers.refresh();
      application.loadBalancers.onNextRefresh($scope, onApplicationRefresh);
    }

    $scope.taskMonitor = new TaskMonitor({
      application: application,
      title: (isNew ? 'Creating ' : 'Updating ') + 'your load balancer',
      modalInstance: $uibModalInstance,
      onTaskComplete: onTaskComplete,
    });

    function initializeEditMode() {
      $scope.state.accountsLoaded = true;
    }

    function initializeCreateMode() {
      AccountService.listAccounts('dcos').then(function(accounts) {
        $scope.accounts = accounts;
        $scope.state.accountsLoaded = true;

        var accountNames = _.map($scope.accounts, 'name');
        if (accountNames.length && !accountNames.includes($scope.loadBalancer.account)) {
          $scope.loadBalancer.account = accountNames[0];
        }

        ctrl.accountUpdated();
      });
    }

    function updateLoadBalancerNames() {
      var account = $scope.loadBalancer.credentials,
        region = $scope.loadBalancer.region;

      const accountLoadBalancersByRegion = {};
      application
        .getDataSource('loadBalancers')
        .refresh(true)
        .then(() => {
          application.getDataSource('loadBalancers').data.forEach(loadBalancer => {
            if (loadBalancer.account === account) {
              accountLoadBalancersByRegion[loadBalancer.region] =
                accountLoadBalancersByRegion[loadBalancer.region] || [];
              accountLoadBalancersByRegion[loadBalancer.region].push(loadBalancer.name);
            }
          });
          $scope.existingLoadBalancerNames = accountLoadBalancersByRegion[region] || [];
        });
    }

    // initialize controller
    if (loadBalancer) {
      $scope.loadBalancer = dcosLoadBalancerTransformer.convertLoadBalancerForEditing(loadBalancer);
      initializeEditMode();
      initializeCreateMode();
    } else {
      $scope.loadBalancer = dcosLoadBalancerTransformer.constructNewLoadBalancerTemplate();
      updateLoadBalancerNames();
      initializeCreateMode();
    }

    // Controller API
    this.updateName = function() {
      $scope.loadBalancer.name = this.getName();
    };

    this.getName = function() {
      var loadBalancer = $scope.loadBalancer;
      var loadBalancerName = [application.name, loadBalancer.stack || '', loadBalancer.detail || ''].join('-');
      return _.trimEnd(loadBalancerName, '-');
    };

    this.accountUpdated = function() {
      AccountService.getAccountDetails($scope.loadBalancer.account).then(function(details) {
        $scope.dcosClusters = details.dcosClusters;
        ctrl.dcosClusterUpdated();
      });
    };

    this.dcosClusterUpdated = function() {
      updateLoadBalancerNames();
      ctrl.updateName();
    };

    this.submit = function() {
      var descriptor = isNew ? 'Create' : 'Update';

      this.updateName();
      $scope.taskMonitor.submit(function() {
        var zones = {};
        zones[$scope.loadBalancer.region] = [$scope.loadBalancer.region];
        let params = {
          cloudProvider: 'dcos',
          availabilityZones: zones,
        };
        return LoadBalancerWriter.upsertLoadBalancer($scope.loadBalancer, application, descriptor, params);
      });
    };

    this.cancel = function() {
      $uibModalInstance.dismiss();
    };
  }]);
