'use strict';

const angular = require('angular');

import { AccountService, LoadBalancerWriter, TaskMonitor, ModalWizard } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.loadBalancer.gce.create.controller', [
    require('@uirouter/angularjs').default,
    require('../../loadBalancer.transformer').name,
    require('../../../gceRegionSelectField.directive').name,
  ])
  .controller('gceCreateLoadBalancerCtrl', function(
    $scope,
    $uibModalInstance,
    $state,
    gceLoadBalancerTransformer,
    application,
    loadBalancer,
    isNew,
  ) {
    const ctrl = this;

    $scope.isNew = isNew;

    $scope.pages = {
      location: require('./createLoadBalancerProperties.html'),
      listeners: require('./listeners.html'),
      healthCheck: require('./healthCheck.html'),
      advancedSettings: require('./advancedSettings.html'),
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
        accountId: $scope.loadBalancer.credentials,
        region: $scope.loadBalancer.region,
        provider: 'gce',
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

    function initializeEditMode() {}

    function initializeCreateMode() {
      AccountService.listAccounts('gce').then(function(accounts) {
        $scope.accounts = accounts;
        $scope.state.accountsLoaded = true;

        const accountNames = _.map($scope.accounts, 'name');
        if (accountNames.length && !accountNames.includes($scope.loadBalancer.credentials)) {
          $scope.loadBalancer.credentials = accountNames[0];
        }

        ctrl.accountUpdated();
      });
    }

    function updateLoadBalancerNames() {
      const account = $scope.loadBalancer.credentials;

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

          $scope.existingLoadBalancerNames = _.flatten(_.map(accountLoadBalancersByRegion));
        });
    }

    // initialize controller
    if (loadBalancer) {
      $scope.loadBalancer = gceLoadBalancerTransformer.convertLoadBalancerForEditing(loadBalancer);
      initializeEditMode();
    } else {
      $scope.loadBalancer = gceLoadBalancerTransformer.constructNewLoadBalancerTemplate();
      updateLoadBalancerNames();
      initializeCreateMode();
    }

    // Controller API

    this.requiresHealthCheckPath = function() {
      return $scope.loadBalancer.healthCheckProtocol && $scope.loadBalancer.healthCheckProtocol.indexOf('HTTP') === 0;
    };

    this.prependForwardSlash = text => {
      return text && text.indexOf('/') !== 0 ? `/${text}` : text;
    };

    this.updateName = function() {
      $scope.loadBalancer.name = this.getName();
    };

    this.getName = function() {
      const loadBalancer = $scope.loadBalancer;
      const loadBalancerName = [application.name, loadBalancer.stack || '', loadBalancer.detail || ''].join('-');
      return _.trimEnd(loadBalancerName, '-');
    };

    this.accountUpdated = function() {
      AccountService.getRegionsForAccount($scope.loadBalancer.credentials).then(function(regions) {
        if (_.isArray(regions)) {
          $scope.regions = _.map(regions, 'name');
        } else {
          // TODO(duftler): Remove this once we finish deprecating the old style regions/zones in clouddriver GCE credentials.
          $scope.regions = _.keys(regions);
        }
        ctrl.regionUpdated();
      });
    };

    this.regionUpdated = function() {
      updateLoadBalancerNames();
      ctrl.updateName();
    };

    this.setVisibilityHealthCheckTab = function() {
      const wizard = ModalWizard;

      if ($scope.loadBalancer.listeners[0].healthCheck) {
        wizard.includePage('Health Check');
        wizard.markIncomplete('Health Check');
        wizard.includePage('Advanced Settings');
        wizard.markIncomplete('Advanced Settings');
      } else {
        wizard.excludePage('Health Check');
        wizard.markComplete('Health Check');
        wizard.excludePage('Advanced Settings');
        wizard.markComplete('Advanced Settings');
        wizard.markComplete('Listener');
      }
    };

    this.submit = function() {
      const descriptor = isNew ? 'Create' : 'Update';

      $scope.taskMonitor.submit(function() {
        const params = {
          cloudProvider: 'gce',
          loadBalancerName: $scope.loadBalancer.name,
        };

        if ($scope.loadBalancer.listeners && $scope.loadBalancer.listeners.length > 0) {
          const listener = $scope.loadBalancer.listeners[0];

          if (listener.protocol) {
            params.ipProtocol = listener.protocol;
          }

          if (listener.portRange) {
            params.portRange = listener.portRange;
          }

          if (listener.healthCheck) {
            params.healthCheck = {
              port: $scope.loadBalancer.healthCheckPort,
              requestPath: $scope.loadBalancer.healthCheckPath,
              timeoutSec: $scope.loadBalancer.healthTimeout,
              checkIntervalSec: $scope.loadBalancer.healthInterval,
              healthyThreshold: $scope.loadBalancer.healthyThreshold,
              unhealthyThreshold: $scope.loadBalancer.unhealthyThreshold,
            };
          } else {
            params.healthCheck = null;
          }
        }

        return LoadBalancerWriter.upsertLoadBalancer($scope.loadBalancer, application, descriptor, params);
      });
    };

    this.cancel = function() {
      $uibModalInstance.dismiss();
    };
  });
