'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.loadBalancer.create.controller', [
  require('angular-ui-router'),
  require('../loadBalancer.write.service.js'),
  require('../../../core/loadBalancer/loadBalancer.read.service.js'),
  require('../../../core/account/account.service.js'),
  require('../loadBalancer.transformer.js'),
  require('../../../core/modal/wizard/v2modalWizard.service.js'),
  require('../../../core/task/monitor/taskMonitorService.js'),
  require('../../../core/cache/cacheInitializer.js'),
  require('../../../core/cache/infrastructureCaches.js'),
  require('../../../core/naming/naming.service.js'),
  require('../../../core/region/regionSelectField.directive.js'),
  require('../../../core/account/accountSelectField.directive.js'),
])
  .controller('azureCreateLoadBalancerCtrl', function($scope, $uibModalInstance, $state, _,
                                                    accountService, azureLoadBalancerTransformer,
                                                    cacheInitializer, infrastructureCaches, loadBalancerReader,
                                                    v2modalWizardService, azureLoadBalancerWriter, taskMonitorService,
                                                    namingService, application, loadBalancer, isNew) {

    var ctrl = this;

    $scope.pages = {
      location: require('./createLoadBalancerProperties.html'),
      listeners: require('./listeners.html'),
      healthCheck: require('./healthCheck.html'),
    };

    $scope.isNew = isNew;

    $scope.state = {
      accountsLoaded: false,
      loadBalancerNamesLoaded: false,
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
        accountId: $scope.loadBalancer.credentials,
        region: $scope.loadBalancer.region,
        provider: 'azure',
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


    $scope.taskMonitor = taskMonitorService.buildTaskMonitor({
      application: application,
      title: (isNew ? 'Creating ' : 'Updating ') + 'your load balancer',
      modalInstance: $uibModalInstance,
      onTaskComplete: onTaskComplete,
    });

    var allLoadBalancerNames = {};

    function initializeCreateMode() {
      accountService.listAccounts('azure').then(function (accounts) {
        $scope.accounts = accounts;
        $scope.state.accountsLoaded = true;
        ctrl.accountUpdated();
      });
    }

    function initializeController() {
      if (loadBalancer) {
        $scope.loadBalancer = azureLoadBalancerTransformer.convertLoadBalancerForEditing(loadBalancer);
        if (isNew) {
          var nameParts = namingService.parseLoadBalancerName($scope.loadBalancer.name);
          $scope.loadBalancer.stack = nameParts.stack;
          $scope.loadBalancer.detail = nameParts.freeFormDetails;
          delete $scope.loadBalancer.name;
        }
      } else {
        $scope.loadBalancer = azureLoadBalancerTransformer.constructNewLoadBalancerTemplate(application);
      }
      if (isNew) {
        initializeLoadBalancerNames();
        initializeCreateMode();
      }
    }

    function initializeLoadBalancerNames() {
      loadBalancerReader.listLoadBalancers('azure').then(function(loadBalancers) {
        loadBalancers.forEach((loadBalancer) => {
          loadBalancer.accounts.forEach((account) => {
            var accountName = account.name;
            account.regions.forEach((region) => {
              var regionName = region.name;
              if (!allLoadBalancerNames[accountName]) {
                allLoadBalancerNames[accountName] = {};
              }
              if (!allLoadBalancerNames[accountName][regionName]) {
                allLoadBalancerNames[accountName][regionName] = [];
              }
              allLoadBalancerNames[accountName][regionName].push(loadBalancer.name);
            });
          });
        });
        updateLoadBalancerNames();
        $scope.state.loadBalancerNamesLoaded = true;
      });
    }

    function updateLoadBalancerNames() {
      var account = $scope.loadBalancer.credentials,
        region = $scope.loadBalancer.region;

      if (allLoadBalancerNames[account] && allLoadBalancerNames[account][region]) {
        $scope.existingLoadBalancerNames = allLoadBalancerNames[account][region];
      } else {
        $scope.existingLoadBalancerNames = [];
      }
    }

    initializeController();

    this.requiresHealthCheckPath = function () {
      return $scope.loadBalancer.probes[0].probeProtocol && $scope.loadBalancer.probes[0].probeProtocol.indexOf('HTTP') === 0;
    };

    this.updateName = function() {
      $scope.loadBalancer.name = this.getName();
    };

    this.getName = function() {
      var elb = $scope.loadBalancer;
      var elbName = [application.name, (elb.stack || ''), (elb.detail || '')].join('-');
      return _.trimRight(elbName, '-');
    };

    this.accountUpdated = function() {
      accountService.getRegionsForAccount($scope.loadBalancer.credentials).then(function(regions) {
        $scope.regions = regions;
        ctrl.regionUpdated();
      });
    };

    this.regionUpdated = function() {
      updateLoadBalancerNames();
      ctrl.updateName();
    };

    this.removeListener = function(index) {
      $scope.loadBalancer.loadBalancingRules.splice(index, 1);
    };

    this.addListener = function() {
      $scope.loadBalancer.loadBalancingRules.push({protocol: 'HTTP'});
    };

    this.submit = function () {
      var descriptor = isNew ? 'Create' : 'Update';

      $scope.taskMonitor.submit(
        function() {
          let params = { cloudProvider: 'azure', appName: application.name, clusterName: $scope.loadBalancer.clusterName,
            resourceGroupName: $scope.loadBalancer.clusterName,
            loadBalancerName: $scope.loadBalancer.name
          };

          var name = $scope.loadBalancer.clusterName || $scope.loadBalancer.name;
          var probeName = name + '-probe';
          var ruleNameBase = name + '-rule';
          $scope.loadBalancer.type = 'upsertLoadBalancer';
          if (!$scope.loadBalancer.vnet && !$scope.loadBalancer.subnetType) {
            $scope.loadBalancer.securityGroups = null;
          }

          $scope.loadBalancer.probes[0].probeName = probeName;

          $scope.loadBalancer.loadBalancingRules.forEach((rule, index) => {
            rule.ruleName = ruleNameBase + index;
            rule.probeName = probeName;
          });

          if ($scope.loadBalancer.probes[0].probeProtocol === 'TCP') {
            $scope.loadBalancer.probes[0].probePath = undefined;
          }

          return azureLoadBalancerWriter.upsertLoadBalancer($scope.loadBalancer, application, descriptor, params);
        }
      );
    };

    this.cancel = function () {
      $uibModalInstance.dismiss();
    };
  });
