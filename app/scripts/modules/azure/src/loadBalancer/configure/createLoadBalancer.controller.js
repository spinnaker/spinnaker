'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { module } from 'angular';
import _ from 'lodash';

import { AccountService, LoadBalancerWriter, NameUtils, NetworkReader, TaskMonitor } from '@spinnaker/core';

import { AZURE_LOADBALANCER_LOADBALANCER_TRANSFORMER } from '../loadBalancer.transformer';

export const AZURE_LOADBALANCER_CONFIGURE_CREATELOADBALANCER_CONTROLLER =
  'spinnaker.azure.loadBalancer.create.controller';
export const name = AZURE_LOADBALANCER_CONFIGURE_CREATELOADBALANCER_CONTROLLER; // for backwards compatibility
module(AZURE_LOADBALANCER_CONFIGURE_CREATELOADBALANCER_CONTROLLER, [
  UIROUTER_ANGULARJS,
  AZURE_LOADBALANCER_LOADBALANCER_TRANSFORMER,
]).controller('azureCreateLoadBalancerCtrl', [
  '$scope',
  '$uibModalInstance',
  '$state',
  'azureLoadBalancerTransformer',
  'application',
  'loadBalancer',
  'isNew',
  'loadBalancerType',
  function (
    $scope,
    $uibModalInstance,
    $state,
    azureLoadBalancerTransformer,
    application,
    loadBalancer,
    isNew,
    loadBalancerType,
  ) {
    const ctrl = this;

    $scope.regions = [];

    $scope.pages = {
      location: require('./createLoadBalancerProperties.html'),
      listeners: require('./listeners.html'),
      healthCheck: require('./healthCheck.html'),
      advancedSettings: require('./advancedSettings.html'),
    };

    $scope.isNew = isNew;
    $scope.loadBalancerType = loadBalancerType.type;
    $scope.isALB = loadBalancerType.type === 'Azure Load Balancer';

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

    $scope.taskMonitor = new TaskMonitor({
      application: application,
      title: (isNew ? 'Creating ' : 'Updating ') + 'your load balancer',
      modalInstance: $uibModalInstance,
      onTaskComplete: onTaskComplete,
    });

    function initializeCreateMode() {
      AccountService.listAccounts('azure').then(function (accounts) {
        $scope.accounts = accounts;
        $scope.state.accountsLoaded = true;
        ctrl.accountUpdated();
      });
    }

    function initializeController() {
      if (loadBalancer) {
        $scope.loadBalancer = azureLoadBalancerTransformer.convertLoadBalancerForEditing(loadBalancer);
        if (isNew) {
          const nameParts = NameUtils.parseLoadBalancerName($scope.loadBalancer.name);
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

    initializeController();

    this.requiresHealthCheckPath = function () {
      return (
        $scope.loadBalancer.probes[0].probeProtocol && $scope.loadBalancer.probes[0].probeProtocol.indexOf('HTTP') === 0
      );
    };

    this.updateName = function () {
      $scope.loadBalancer.name = this.getName();
    };

    this.getName = function () {
      const elb = $scope.loadBalancer;
      const elbName = [application.name, elb.stack || '', elb.detail || ''].join('-');
      return _.trimEnd(elbName, '-');
    };

    this.accountUpdated = function () {
      AccountService.getRegionsForAccount($scope.loadBalancer.credentials).then(function (regions) {
        $scope.regions = regions;
        ctrl.regionUpdated();
      });
    };

    this.regionUpdated = function () {
      updateLoadBalancerNames();
      ctrl.updateName();
      ctrl.vnetUpdated();
    };

    this.vnetUpdated = function () {
      const account = $scope.loadBalancer.credentials;
      const region = $scope.loadBalancer.region;
      $scope.loadBalancer.selectedVnet = null;
      $scope.loadBalancer.vnet = null;
      $scope.loadBalancer.vnetResourceGroup = null;
      ctrl.selectedVnets = [];

      NetworkReader.listNetworks().then(function (vnets) {
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

    this.subnetUpdated = function () {
      $scope.loadBalancer.selectedSubnet = null;
      $scope.loadBalancer.subnet = null;
      ctrl.selectedSubnets = [];
    };

    this.selectedVnetChanged = function (item) {
      $scope.loadBalancer.vnet = item.name;
      $scope.loadBalancer.vnetResourceGroup = item.resourceGroup;
      $scope.loadBalancer.selectedSubnet = null;
      $scope.loadBalancer.subnet = null;
      ctrl.selectedSubnets = [];
      if (item.subnets) {
        item.subnets.map(function (subnet) {
          let addSubnet = true;
          if (subnet.devices) {
            subnet.devices.map(function (device) {
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

    this.removeListener = function (index) {
      $scope.loadBalancer.loadBalancingRules.splice(index, 1);
    };

    this.addListener = function () {
      $scope.loadBalancer.loadBalancingRules.push({ protocol: 'HTTP' });
    };

    this.submit = function () {
      const descriptor = isNew ? 'Create' : 'Update';

      $scope.taskMonitor.submit(function () {
        const params = {
          cloudProvider: 'azure',
          appName: application.name,
          clusterName: $scope.loadBalancer.clusterName,
          resourceGroupName: $scope.loadBalancer.clusterName,
          loadBalancerName: $scope.loadBalancer.name,
        };

        if ($scope.loadBalancer.selectedVnet) {
          $scope.loadBalancer.vnet = $scope.loadBalancer.selectedVnet.name;
          $scope.loadBalancer.vnetResourceGroup = $scope.loadBalancer.selectedVnet.resourceGroup;
        }

        if ($scope.loadBalancer.selectedSubnet) {
          $scope.loadBalancer.subnet = $scope.loadBalancer.selectedSubnet.name;
        }

        const name = $scope.loadBalancer.clusterName || $scope.loadBalancer.name;
        const probeName = name + '-probe';
        const ruleNameBase = name + '-rule';
        $scope.loadBalancer.type = 'upsertLoadBalancer';
        $scope.loadBalancer.loadBalancerType = $scope.loadBalancerType;
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

        return LoadBalancerWriter.upsertLoadBalancer($scope.loadBalancer, application, descriptor, params);
      });
    };

    this.cancel = function () {
      $uibModalInstance.dismiss();
    };
  },
]);
