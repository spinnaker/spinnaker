'use strict';

import _ from 'lodash';

import {
  CloudProviderRegistry,
  CONFIRMATION_MODAL_SERVICE,
  InstanceReader,
  INSTANCE_WRITE_SERVICE,
  RecentHistoryService,
  ServerGroupTemplates,
} from '@spinnaker/core';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.dcos.instance.details.controller', [INSTANCE_WRITE_SERVICE, CONFIRMATION_MODAL_SERVICE])
  .controller('dcosInstanceDetailsController', [
    '$scope',
    '$state',
    '$uibModal',
    'instanceWriter',
    'confirmationModalService',
    'instance',
    'app',
    'dcosProxyUiService',
    '$q',
    function(
      $scope,
      $state,
      $uibModal,
      instanceWriter,
      confirmationModalService,
      instance,
      app,
      dcosProxyUiService,
      $q,
    ) {
      // needed for standalone instances
      $scope.detailsTemplateUrl = CloudProviderRegistry.getValue('dcos', 'instance.detailsTemplateUrl');

      $scope.state = {
        loading: true,
        standalone: app.isStandalone,
      };

      this.uiLink = function uiLink() {
        return dcosProxyUiService.buildLink(
          $scope.instance.clusterUrl,
          $scope.instance.account,
          $scope.instance.region,
          $scope.instance.serverGroupName,
          $scope.instance.name,
        );
      };

      this.showJson = function showJson() {
        $scope.userDataModalTitle = 'Task JSON';
        $scope.userData = $scope.instance.json;
        $uibModal.open({
          templateUrl: ServerGroupTemplates.userData,
          scope: $scope,
        });
      };

      function retrieveInstance() {
        var extraData = {};
        var instanceSummary, loadBalancers, account, region;
        app.serverGroups.data.some(function(serverGroup) {
          return serverGroup.instances.some(function(possibleInstance) {
            if (possibleInstance.id === instance.instanceId) {
              instanceSummary = possibleInstance;
              loadBalancers = serverGroup.loadBalancers;
              account = serverGroup.account;
              region = serverGroup.region;
              extraData.serverGroup = serverGroup.name;
              return true;
            }
          });
        });

        if (instanceSummary && account && region) {
          extraData.account = account;
          extraData.region = region;
          RecentHistoryService.addExtraDataToLatest('instances', extraData);
          return InstanceReader.getInstanceDetails(account, region, instance.instanceId).then(function(details) {
            $scope.state.loading = false;
            $scope.instance = _.defaults(details, instanceSummary);
            $scope.instance.account = account;
            $scope.instance.serverGroupName = extraData.serverGroup;
            $scope.instance.region = region;
            $scope.instance.loadBalancers = loadBalancers;
          }, autoClose);
        }

        if (!instanceSummary) {
          autoClose();
        }
        return $q.when(null);
      }

      function autoClose() {
        if ($scope.$$destroyed) {
          return;
        }
        $state.go('^', { allowModalToStayOpen: true }, { location: 'replace' });
      }

      this.terminateInstance = function terminateInstance() {
        var instance = $scope.instance;

        var taskMonitor = {
          application: app,
          title: 'Terminating ' + instance.instanceId,
          onTaskComplete: function() {
            if ($state.includes('**.instanceDetails', { instanceId: instance.instanceId })) {
              $state.go('^');
            }
          },
        };

        var submitMethod = function() {
          let params = { cloudProvider: 'dcos' };

          if (instance.serverGroup) {
            params.managedInstanceGroupName = instance.serverGroup;
          }

          params.namespace = instance.namespace;
          instance.placement = {};

          return instanceWriter.terminateInstance(instance, app, params);
        };

        confirmationModalService.confirm({
          header: 'Really terminate ' + instance.instanceId + '?',
          buttonText: 'Terminate ' + instance.instanceId,
          account: instance.account,
          provider: 'dcos',
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod,
        });
      };

      this.registerInstanceWithLoadBalancer = function registerInstanceWithLoadBalancer() {
        var instance = $scope.instance;
        var loadBalancerNames = instance.loadBalancers.join(' and ');

        var taskMonitor = {
          application: app,
          title: 'Registering ' + instance.name + ' with ' + loadBalancerNames,
        };

        var submitMethod = function() {
          return instanceWriter.registerInstanceWithLoadBalancer(instance, app, {
            interestingHealthProviderNames: ['Dcos'],
          });
        };

        confirmationModalService.confirm({
          header: 'Really register ' + instance.name + ' with ' + loadBalancerNames + '?',
          buttonText: 'Register ' + instance.name,
          account: instance.account,
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod,
        });
      };

      this.deregisterInstanceFromLoadBalancer = function deregisterInstanceFromLoadBalancer() {
        var instance = $scope.instance;
        var loadBalancerNames = instance.loadBalancers.join(' and ');

        var taskMonitor = {
          application: app,
          title: 'Deregistering ' + instance.name + ' from ' + loadBalancerNames,
        };

        var submitMethod = function() {
          return instanceWriter.deregisterInstanceFromLoadBalancer(instance, app, {
            interestingHealthProviderNames: ['Dcos'],
          });
        };

        confirmationModalService.confirm({
          header: 'Really deregister ' + instance.name + ' from ' + loadBalancerNames + '?',
          buttonText: 'Deregister ' + instance.name,
          provider: 'dcos',
          account: instance.account,
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod,
        });
      };

      this.canRegisterWithLoadBalancer = function() {
        return false;
      };

      this.canDeregisterFromLoadBalancer = function() {
        return false;
      };

      this.hasHealthState = function hasHealthState(healthProviderType, state) {
        var instance = $scope.instance;
        return instance.health.some(function(health) {
          return health.type === healthProviderType && health.state === state;
        });
      };

      let initialize = app.isStandalone
        ? retrieveInstance()
        : $q.all([app.serverGroups.ready(), app.loadBalancers.ready()]).then(retrieveInstance);

      initialize.then(() => {
        // Two things to look out for here:
        //  1. If the retrieveInstance call completes *after* the user has navigated away from the view, there
        //     is no point in subscribing to the refresh
        //  2. If this is a standalone instance, there is no application that will refresh
        if (!$scope.$$destroyed && !app.isStandalone) {
          app.serverGroups.onRefresh($scope, retrieveInstance);
        }
      });

      $scope.account = instance.account;
    },
  ]);
