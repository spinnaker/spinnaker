'use strict';

import _ from 'lodash';

import {V2_MODAL_WIZARD_SERVICE} from 'core/modal/wizard/v2modalWizard.service';
import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {INFRASTRUCTURE_CACHE_SERVICE} from 'core/cache/infrastructureCaches.service';
import {NAMING_SERVICE} from 'core/naming/naming.service';
import {NETWORK_READ_SERVICE} from 'core/network/network.read.service';
import {TASK_MONITOR_BUILDER} from 'core/task/monitor/taskMonitor.builder';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.loadBalancer.create.controller', [
  require('angular-ui-router'),
  require('../loadBalancer.write.service.js'),
  ACCOUNT_SERVICE,
  require('../loadBalancer.transformer.js'),
  V2_MODAL_WIZARD_SERVICE,
  TASK_MONITOR_BUILDER,
  INFRASTRUCTURE_CACHE_SERVICE,
  NAMING_SERVICE,
  require('core/region/regionSelectField.directive.js'),
  require('core/account/accountSelectField.directive.js'),
  NETWORK_READ_SERVICE,
])
  .controller('azureCreateLoadBalancerCtrl', function($scope, $uibModalInstance, $state,
                                                      accountService, azureLoadBalancerTransformer,
                                                      infrastructureCaches, networkReader,
                                                      v2modalWizardService, azureLoadBalancerWriter, taskMonitorBuilder,
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


    $scope.taskMonitor = taskMonitorBuilder.buildTaskMonitor({
      application: application,
      title: (isNew ? 'Creating ' : 'Updating ') + 'your load balancer',
      modalInstance: $uibModalInstance,
      onTaskComplete: onTaskComplete,
    });

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
        updateLoadBalancerNames();
        initializeCreateMode();
      }
    }

    function updateLoadBalancerNames() {
      var account = $scope.loadBalancer.credentials,
          region = $scope.loadBalancer.region;

      const accountLoadBalancersByRegion = {};
      application.getDataSource('loadBalancers').refresh(true).then(() => {
        application.getDataSource('loadBalancers').data.forEach((loadBalancer) => {
          if (loadBalancer.account === account) {
            accountLoadBalancersByRegion[loadBalancer.region] = accountLoadBalancersByRegion[loadBalancer.region] || [];
            accountLoadBalancersByRegion[loadBalancer.region].push(loadBalancer.name);
          }
        });

        $scope.existingLoadBalancerNames = accountLoadBalancersByRegion[region] || [];
      });
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
      return _.trimEnd(elbName, '-');
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
      ctrl.vnetUpdated();
    };

    this.vnetUpdated = function() {
      var account = $scope.loadBalancer.credentials,
          region = $scope.loadBalancer.region;
      $scope.loadBalancer.selectedVnet = null;
      $scope.loadBalancer.vnet = null;
      $scope.loadBalancer.vnetResourceGroup = null;
      ctrl.selectedVnets = [ ];
      infrastructureCaches.clearCache('networks');

      networkReader.listNetworks().then(function(vnets) {
        if (vnets.azure) {
          vnets.azure.forEach((vnet) => {
            if (vnet.account === account && vnet.region === region) {
              ctrl.selectedVnets.push(vnet);
            }
          });
        }
      });

      ctrl.subnetUpdated();
    };

    this.subnetUpdated = function() {
      $scope.loadBalancer.selectedSubnet = null;
      $scope.loadBalancer.subnet = null;
      ctrl.selectedSubnets = [ ];
    };

    this.selectedVnetChanged = function(item) {
      $scope.loadBalancer.vnet = item.name;
      $scope.loadBalancer.vnetResourceGroup = item.resourceGroup;
      $scope.loadBalancer.selectedSubnet = null;
      $scope.loadBalancer.subnet = null;
      ctrl.selectedSubnets = [ ];
      if (item.subnets) {
        item.subnets.map(function(subnet) {
          var addSubnet = true;
          if (subnet.devices) {
            subnet.devices.map(function(device) {
              if (device && device.type !== 'applicationGateways') {
                addSubnet = false;
              }
            });
          }
          if (addSubnet) {
            ctrl.selectedSubnets.push(subnet);
          }
        });
      }
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

          if($scope.loadBalancer.selectedVnet) {
            $scope.loadBalancer.vnet = $scope.loadBalancer.selectedVnet.name;
            $scope.loadBalancer.vnetResourceGroup = $scope.loadBalancer.selectedVnet.resourceGroup;
          }

          if($scope.loadBalancer.selectedSubnet) {
            $scope.loadBalancer.subnet = $scope.loadBalancer.selectedSubnet.name;
          }

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
