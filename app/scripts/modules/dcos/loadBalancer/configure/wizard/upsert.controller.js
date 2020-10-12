'use strict';

import { module } from 'angular';

import { AccountService, LOAD_BALANCER_READ_SERVICE, LoadBalancerWriter, TaskMonitor } from '@spinnaker/core';
import { DCOS_LOADBALANCER_TRANSFORMER } from '../../transformer';

export const DCOS_LOADBALANCER_CONFIGURE_WIZARD_UPSERT_CONTROLLER = 'spinnaker.dcos.loadBalancer.create.controller';
export const name = DCOS_LOADBALANCER_CONFIGURE_WIZARD_UPSERT_CONTROLLER; // for backwards compatibility
module(DCOS_LOADBALANCER_CONFIGURE_WIZARD_UPSERT_CONTROLLER, [
  LOAD_BALANCER_READ_SERVICE,
  DCOS_LOADBALANCER_TRANSFORMER,
]).controller('dcosUpsertLoadBalancerController', [
  '$scope',
  '$uibModalInstance',
  '$state',
  'application',
  'loadBalancer',
  'isNew',
  'loadBalancerReader',
  'dcosLoadBalancerTransformer',
  function (
    $scope,
    $uibModalInstance,
    $state,
    application,
    loadBalancer,
    isNew,
    loadBalancerReader,
    dcosLoadBalancerTransformer,
  ) {
    const ctrl = this;
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
      const newStateParams = {
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
      AccountService.listAccounts('dcos').then(function (accounts) {
        $scope.accounts = accounts;
        $scope.state.accountsLoaded = true;

        const accountNames = _.map($scope.accounts, 'name');
        if (accountNames.length && !accountNames.includes($scope.loadBalancer.account)) {
          $scope.loadBalancer.account = accountNames[0];
        }

        ctrl.accountUpdated();
      });
    }

    function updateLoadBalancerNames() {
      const account = $scope.loadBalancer.credentials;
      const region = $scope.loadBalancer.region;

      const accountLoadBalancersByRegion = {};
      application
        .getDataSource('loadBalancers')
        .refresh(true)
        .then(() => {
          application.getDataSource('loadBalancers').data.forEach((loadBalancer) => {
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
    this.updateName = function () {
      $scope.loadBalancer.name = this.getName();
    };

    this.getName = function () {
      const loadBalancer = $scope.loadBalancer;
      const loadBalancerName = [application.name, loadBalancer.stack || '', loadBalancer.detail || ''].join('-');
      return _.trimEnd(loadBalancerName, '-');
    };

    this.accountUpdated = function () {
      AccountService.getAccountDetails($scope.loadBalancer.account).then(function (details) {
        $scope.dcosClusters = details.dcosClusters;
        ctrl.dcosClusterUpdated();
      });
    };

    this.dcosClusterUpdated = function () {
      updateLoadBalancerNames();
      ctrl.updateName();
    };

    this.submit = function () {
      const descriptor = isNew ? 'Create' : 'Update';

      this.updateName();
      $scope.taskMonitor.submit(function () {
        const zones = {};
        zones[$scope.loadBalancer.region] = [$scope.loadBalancer.region];
        const params = {
          cloudProvider: 'dcos',
          availabilityZones: zones,
        };
        return LoadBalancerWriter.upsertLoadBalancer($scope.loadBalancer, application, descriptor, params);
      });
    };

    this.cancel = function () {
      $uibModalInstance.dismiss();
    };
  },
]);
