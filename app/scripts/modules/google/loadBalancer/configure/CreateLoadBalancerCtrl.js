'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.loadBalancer.gce.create.controller', [
  require('../../../loadBalancers/loadBalancer.write.service.js'),
  require('../../../loadBalancers/loadBalancer.read.service.js'),
  require('../../../account/account.service.js'),
  require('../loadBalancer.transformer.js'),
  require('../../../securityGroups/securityGroup.read.service.js'),
  require('../../../modal/wizard/modalWizard.service.js'),
  require('../../../tasks/monitor/taskMonitorService.js'),
  require('../../../google/gceRegionSelectField.directive.js'),
  require('../../../search/search.service.js'),
])
  .controller('gceCreateLoadBalancerCtrl', function($scope, $modalInstance, $state, $exceptionHandler,
                                                 application, loadBalancer, isNew, loadBalancerReader,
                                                 accountService, gceLoadBalancerTransformer, securityGroupReader,
                                                 _, searchService, modalWizardService, loadBalancerWriter, taskMonitorService) {

    var ctrl = this;

    $scope.isNew = isNew;

    $scope.pages = {
      location: require('./createLoadBalancerProperties.html'),
      securityGroups: require('./securityGroups.html'),
      listeners: require('./listeners.html'),
      healthCheck: require('./healthCheck.html'),
    };

    $scope.state = {
      securityGroupsLoaded: false,
      accountsLoaded: false,
      loadBalancerNamesLoaded: false,
      submitting: false
    };

    $scope.taskMonitor = taskMonitorService.buildTaskMonitor({
      application: application,
      title: (isNew ? 'Creating ' : 'Updating ') + 'your load balancer',
      forceRefreshMessage: 'Getting your new load balancer from GCE...',
      modalInstance: $modalInstance,
    });

    var allSecurityGroups = {},
        allLoadBalancerNames = {};

    function initializeEditMode() {
      if ($scope.loadBalancer.vpcId) {
        preloadSecurityGroups().then(function() {
          updateAvailableSecurityGroups([$scope.loadBalancer.vpcId]);
        });
      }
    }

    function initializeCreateMode() {
      preloadSecurityGroups();
      accountService.listAccounts('gce').then(function (accounts) {
        $scope.accounts = accounts;
        $scope.state.accountsLoaded = true;

        var accountNames = _.pluck($scope.accounts, 'name');
        if (accountNames.length && accountNames.indexOf($scope.loadBalancer.credentials) === -1) {
          $scope.loadBalancer.credentials = accountNames[0];
        }

        ctrl.accountUpdated();
      });
    }

    function preloadSecurityGroups() {
      return securityGroupReader.getAllSecurityGroups().then(function (securityGroups) {
        allSecurityGroups = securityGroups;
        $scope.state.securityGroupsLoaded = true;
      });
    }

    function initializeLoadBalancerNames() {
      loadBalancerReader.listLoadBalancers('gce').then(function (loadBalancers) {
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

    function updateAvailableSecurityGroups(availableVpcIds) {
      var account = $scope.loadBalancer.credentials,
        region = $scope.loadBalancer.region;

      if (account && region && allSecurityGroups[account] && allSecurityGroups[account].aws[region]) {
        $scope.availableSecurityGroups = _.filter(allSecurityGroups[account].aws[region], function(securityGroup) {
          return availableVpcIds.indexOf(securityGroup.vpcId) !== -1;
        });
        $scope.existingSecurityGroupNames = _.collect($scope.availableSecurityGroups, 'name');
        var existingNames = ['nf-datacenter-vpc', 'nf-infrastructure-vpc'];
        $scope.loadBalancer.securityGroups.forEach(function(securityGroup) {
          if ($scope.existingSecurityGroupNames.indexOf(securityGroup) === -1) {
            var matches = _.filter($scope.availableSecurityGroups, {id: securityGroup});
            if (matches.length) {
              existingNames.push(matches[0].name);
            }
          } else {
            existingNames.push(securityGroup);
          }
        });
        $scope.loadBalancer.securityGroups = _.unique(existingNames);
      } else {
        clearSecurityGroups();
      }
    }

    function updateLoadBalancerNames() {
      var account = $scope.loadBalancer.credentials;

      if (allLoadBalancerNames[account]) {
        $scope.existingLoadBalancerNames = _.flatten(_.map(allLoadBalancerNames[account]));
      } else {
        $scope.existingLoadBalancerNames = [];
      }
    }

    function clearSecurityGroups() {
      $scope.availableSecurityGroups = [];
      $scope.existingSecurityGroupNames = [];
    }

    // initialize controller
    if (loadBalancer) {
      $scope.loadBalancer = gceLoadBalancerTransformer.convertLoadBalancerForEditing(loadBalancer);
      initializeEditMode();
    } else {
      $scope.loadBalancer = gceLoadBalancerTransformer.constructNewLoadBalancerTemplate();
      initializeLoadBalancerNames();
      initializeCreateMode();
    }

    // Controller API

    this.requiresHealthCheckPath = function () {
      return $scope.loadBalancer.healthCheckProtocol && $scope.loadBalancer.healthCheckProtocol.indexOf('HTTP') === 0;
    };

    this.updateName = function() {
      $scope.loadBalancer.name = this.getName();
    };

    this.getName = function() {
      var elb = $scope.loadBalancer;
      return [application.name, (elb.stack || ''), (elb.detail || '')].join('-');
    };

    this.accountUpdated = function() {
      accountService.getRegionsForAccount($scope.loadBalancer.credentials).then(function(regions) {
        $scope.regions = Object.keys(regions);
        clearSecurityGroups();
        ctrl.regionUpdated();
      });
    };

    this.regionUpdated = function() {
      updateLoadBalancerNames();
      ctrl.updateName();
    };

    this.setVisibilityHealthCheckTab = function() {
      var wizard = modalWizardService.getWizard();

      if ($scope.loadBalancer.listeners[0].healthCheck) {
        wizard.includePage('Health Check');
        wizard.markIncomplete('Health Check');
      } else {
        wizard.excludePage('Health Check');
        wizard.markComplete('Health Check');
        wizard.markComplete('Listener');
      }
    };

    $scope.taskMonitor.onApplicationRefresh = function handleApplicationRefreshComplete() {
      $modalInstance.close();
      var newStateParams = {
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
    };


    this.submit = function () {
      var descriptor = isNew ? 'Create' : 'Update';

      $scope.taskMonitor.submit(
        function() {
          let params = {
            cloudProvider: 'gce',
            networkLoadBalancerName: $scope.loadBalancer.name,
          };

          if ($scope.loadBalancer.listeners && $scope.loadBalancer.listeners.length > 0) {
            let listener = $scope.loadBalancer.listeners[0];

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

          return loadBalancerWriter.upsertLoadBalancer($scope.loadBalancer, application, descriptor, params);
        }
      );
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  }).name;
