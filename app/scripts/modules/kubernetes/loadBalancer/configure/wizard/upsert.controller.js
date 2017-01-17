'use strict';

let angular = require('angular');

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {LOAD_BALANCER_READ_SERVICE} from 'core/loadBalancer/loadBalancer.read.service';
import {LOAD_BALANCER_WRITE_SERVICE} from 'core/loadBalancer/loadBalancer.write.service';
import {V2_MODAL_WIZARD_SERVICE} from 'core/modal/wizard/v2modalWizard.service';
import {TASK_MONITOR_BUILDER} from 'core/task/monitor/taskMonitor.builder';

module.exports = angular.module('spinnaker.loadBalancer.kubernetes.create.controller', [
  require('angular-ui-router'),
  LOAD_BALANCER_WRITE_SERVICE,
  LOAD_BALANCER_READ_SERVICE,
  ACCOUNT_SERVICE,
  V2_MODAL_WIZARD_SERVICE,
  TASK_MONITOR_BUILDER,
  require('core/search/search.service.js'),
  require('../../../namespace/selectField.directive.js'),
  require('../../transformer.js'),
])
  .controller('kubernetesUpsertLoadBalancerController', function($scope, $uibModalInstance, $state,
                                                                 application, loadBalancer, isNew, loadBalancerReader,
                                                                 accountService, kubernetesLoadBalancerTransformer,
                                                                 searchService, v2modalWizardService, loadBalancerWriter, taskMonitorBuilder) {
    var ctrl = this;
    $scope.isNew = isNew;

    $scope.pages = {
      basicSettings: require('./basicSettings.html'),
      ports: require('./ports.html'),
      advancedSettings: require('./advancedSettings.html'),
    };

    $scope.state = {
      accountsLoaded: false,
      loadBalancerNamesLoaded: false,
      submitting: false
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
        provider: 'kubernetes',
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

    $scope.taskMonitor = taskMonitorBuilder.buildTaskMonitor({
      application: application,
      title: (isNew ? 'Creating ' : 'Updating ') + 'your load balancer',
      modalInstance: $uibModalInstance,
      onTaskComplete: onTaskComplete,
    });

    var allLoadBalancerNames = {};

    function initializeEditMode() {
    }

    function initializeCreateMode() {
      accountService.listAccounts('kubernetes').then(function (accounts) {
        $scope.accounts = accounts;
        $scope.state.accountsLoaded = true;

        var accountNames = _.map($scope.accounts, 'name');
        if (accountNames.length && !accountNames.includes($scope.loadBalancer.account)) {
          $scope.loadBalancer.account = accountNames[0];
        }

        ctrl.accountUpdated();
      });
    }

    function initializeLoadBalancerNames() {
      loadBalancerReader.listLoadBalancers('kubernetes').then(function (loadBalancers) {
        loadBalancers.forEach((loadBalancer) => {
          let account = loadBalancer.account;
          if (!allLoadBalancerNames[account]) {
            allLoadBalancerNames[account] = {};
          }
          let namespace = loadBalancer.namespace;
          if (!allLoadBalancerNames[account][namespace]) {
            allLoadBalancerNames[account][namespace] = [];
          }
          allLoadBalancerNames[account][namespace].push(loadBalancer.name);
        });

        updateLoadBalancerNames();
        $scope.state.loadBalancerNamesLoaded = true;
      });
    }

    function updateLoadBalancerNames() {
      var account = $scope.loadBalancer.account;
      var namespace = $scope.loadBalancer.namespace;

      $scope.existingLoadBalancerNames = [];
      if (account && namespace &&
          allLoadBalancerNames[account] &&
          allLoadBalancerNames[account][namespace]) {
        $scope.existingLoadBalancerNames = allLoadBalancerNames[account][namespace];
      }
    }

    // initialize controller
    if (loadBalancer) {
      $scope.loadBalancer = kubernetesLoadBalancerTransformer.convertLoadBalancerForEditing(loadBalancer);
      initializeEditMode();
      initializeCreateMode();
    } else {
      $scope.loadBalancer = kubernetesLoadBalancerTransformer.constructNewLoadBalancerTemplate();
      initializeLoadBalancerNames();
      initializeCreateMode();
    }

    // Controller API
    this.updateName = function() {
      $scope.loadBalancer.name = this.getName();
    };

    this.getName = function() {
      var loadBalancer = $scope.loadBalancer;
      var loadBalancerName = [application.name, (loadBalancer.stack || ''), (loadBalancer.detail || '')].join('-');
      return _.trimEnd(loadBalancerName, '-');
    };

    this.accountUpdated = function() {
      accountService.getAccountDetails($scope.loadBalancer.account).then(function(details) {
        $scope.namespaces = details.namespaces;
        ctrl.namespaceUpdated();
      });
    };

    this.namespaceUpdated = function() {
      updateLoadBalancerNames();
      ctrl.updateName();
    };

    this.submit = function () {
      var descriptor = isNew ? 'Create' : 'Update';

      this.updateName();
      $scope.taskMonitor.submit(
        function() {
          var zones = {};
          // TODO(lwander) make generic Q2 2016
          zones[$scope.loadBalancer.namespace] = [$scope.loadBalancer.namespace];
          let params = {
            cloudProvider: 'kubernetes',
            availabilityZones: zones
          };
          return loadBalancerWriter.upsertLoadBalancer($scope.loadBalancer, application, descriptor, params);
        }
      );
    };

    this.cancel = function () {
      $uibModalInstance.dismiss();
    };
  });
