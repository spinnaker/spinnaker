'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.instance.detail.cf.controller', [
  require('angular-ui-router'),
  require('angular-ui-bootstrap'),
  require('../../../core/instance/instance.write.service.js'),
  require('../../../core/instance/instance.read.service.js'),
  require('../../../core/confirmationModal/confirmationModal.service.js'),
  require('../../../core/utils/lodash.js'),
  require('../../../core/insight/insightFilterState.model.js'),
  require('../../../core/history/recentHistory.service.js'),
  require('../../../core/utils/selectOnDblClick.directive.js'),
  require('../../../core/cloudProvider/cloudProvider.registry.js'),
])
  .controller('cfInstanceDetailsCtrl', function ($scope, $q, $state, $uibModal, InsightFilterStateModel,
                                                 instanceWriter, confirmationModalService, recentHistoryService,
                                                 cloudProviderRegistry, instanceReader, _, instance, app) {

    // needed for standalone instances
    $scope.detailsTemplateUrl = cloudProviderRegistry.getValue('cf', 'instance.detailsTemplateUrl');

    $scope.state = {
      loading: true,
      standalone: app.isStandalone,
    };

    $scope.InsightFilterStateModel = InsightFilterStateModel;

    function extractHealthMetrics(instance, latest) {
      // do not backfill on standalone instances
      if (app.isStandalone) {
        instance.health = latest.health;
      }

      instance.health = instance.health || [];
      var displayableMetrics = instance.health.filter(
        function(metric) {
          return metric.type !== 'cf' || metric.state !== 'Unknown';
        });

      // backfill details where applicable
      if (latest.health) {
        displayableMetrics.forEach(function (metric) {
          var detailsMatch = latest.health.filter(function (latestHealth) {
            return latestHealth.type === metric.type;
          });
          if (detailsMatch.length) {
            _.defaults(metric, detailsMatch[0]);
          }
        });
      }
      $scope.healthMetrics = displayableMetrics;
    }

    function retrieveInstance() {
      var extraData = {};
      var instanceSummary, loadBalancers, account, region, vpcId;
      if (!app.serverGroups) {
        // standalone instance
        instanceSummary = {};
        loadBalancers = [];
        account = instance.account;
        region = instance.region;
      } else {
        app.serverGroups.some(function (serverGroup) {
          return serverGroup.instances.some(function (possibleInstance) {
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
        if (!instanceSummary) {
          // perhaps it is in a server group that is part of another application
          app.loadBalancers.some(function (loadBalancer) {
            return loadBalancer.instances.some(function (possibleInstance) {
              if (possibleInstance.id === instance.instanceId) {
                instanceSummary = possibleInstance;
                loadBalancers = [loadBalancer.name];
                account = loadBalancer.account;
                region = loadBalancer.region;
                vpcId = loadBalancer.vpcId;
                return true;
              }
            });
          });
          if (!instanceSummary) {
            // perhaps it is in a disabled server group via a load balancer
            app.loadBalancers.some(function (loadBalancer) {
              return loadBalancer.serverGroups.some(function (serverGroup) {
                if (!serverGroup.isDisabled) {
                  return false;
                }
                return serverGroup.instances.some(function (possibleInstance) {
                  if (possibleInstance.id === instance.instanceId) {
                    instanceSummary = possibleInstance;
                    loadBalancers = [loadBalancer.name];
                    account = loadBalancer.account;
                    region = loadBalancer.region;
                    vpcId = loadBalancer.vpcId;
                    return true;
                  }
                });
              });
            });
          }
        }
      }

      if (instanceSummary && account && region) {
        extraData.account = account;
        extraData.region = region;
        recentHistoryService.addExtraDataToLatest('instances', extraData);
        return instanceReader.getInstanceDetails(account, region, instance.instanceId).then(function(details) {
          details = details.plain();
          $scope.state.loading = false;
          extractHealthMetrics(instanceSummary, details);
          $scope.instance = _.defaults(details, instanceSummary);
          $scope.instance.instanceId = $scope.instance.id;
          $scope.instance.account = account;
          $scope.instance.region = region;
          $scope.instance.vpcId = vpcId;
          $scope.instance.loadBalancers = loadBalancers;
          $scope.baseIpAddress = details.publicDnsName || details.privateIpAddress;

          $scope.instance.internalDnsName = $scope.instance.instanceId;

          // TODO Add link to CF console outputs
        },
          autoClose
        );
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
      $state.params.allowModalToStayOpen = true;
      $state.go('^', null, {location: 'replace'});
    }

    this.canRegisterWithLoadBalancer = function() {
      var instance = $scope.instance;
      if (!instance.loadBalancers || !instance.loadBalancers.length) {
        return false;
      }
      var outOfService = instance.health.some(function(health) {
        return health.type === 'LoadBalancer' && health.state === 'OutOfService';
      });
      var hasLoadBalancerHealth = instance.health.some(function(health) {
        return health.type === 'LoadBalancer';
      });
      return outOfService || !hasLoadBalancerHealth;
    };

    this.canDeregisterFromLoadBalancer = function() {
      var instance = $scope.instance;
      if (!instance.loadBalancers || !instance.loadBalancers.length) {
        return false;
      }
      var hasLoadBalancerHealth = instance.health.some(function(health) {
        return health.type === 'LoadBalancer';
      });
      return hasLoadBalancerHealth;
    };

    this.canRegisterWithDiscovery = function() {
      var instance = $scope.instance;
      var discoveryHealth = instance.health.filter(function(health) {
        return health.type === 'Discovery';
      });
      return discoveryHealth.length ? discoveryHealth[0].state === 'OutOfService' : false;
    };

    this.terminateInstance = function terminateInstance() {
      var instance = $scope.instance;

      var taskMonitor = {
        application: app,
        title: 'Terminating ' + instance.instanceId,
        forceRefreshMessage: 'Refreshing application...',
        forceRefreshEnabled: true,
        onApplicationRefresh: function() {
          if ($state.includes('**.instanceDetails', {instanceId: instance.instanceId})) {
            $state.go('^');
          }
        }
      };

      var submitMethod = function () {
        let params = {cloudProvider: 'cf'};

        if (instance.serverGroup) {
          params.managedInstanceGroupName = instance.serverGroup;
        }

        return instanceWriter.terminateInstance(instance, app, params);
      };

      confirmationModalService.confirm({
        header: 'Really terminate ' + instance.instanceId + '?',
        buttonText: 'Terminate ' + instance.instanceId,
        destructive: true,
        account: instance.account,
        provider: 'cf',
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

    this.rebootInstance = function rebootInstance() {
      var instance = $scope.instance;

      var taskMonitor = {
        application: app,
        title: 'Rebooting ' + instance.instanceId
      };

      var submitMethod = function () {
        return instanceWriter.rebootInstance(instance, app);
      };

      confirmationModalService.confirm({
        header: 'Really reboot ' + instance.instanceId + '?',
        buttonText: 'Reboot ' + instance.instanceId,
        destructive: true,
        account: instance.account,
        provider: 'cf',
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

    this.registerInstanceWithLoadBalancer = function registerInstanceWithLoadBalancer() {
      var instance = $scope.instance;
      var loadBalancerNames = instance.loadBalancers.join(' and ');

      var taskMonitor = {
        application: app,
        title: 'Registering ' + instance.instanceId + ' with ' + loadBalancerNames
      };

      var submitMethod = function () {
        return instanceWriter.registerInstanceWithLoadBalancer(instance, app, {
          cloudProvider: 'cf',
          networkLoadBalancerNames: instance.loadBalancers,
        });
      };

      confirmationModalService.confirm({
        header: 'Really register ' + instance.instanceId + ' with ' + loadBalancerNames + '?',
        buttonText: 'Register ' + instance.instanceId,
        destructive: false,
        account: instance.account,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

    this.deregisterInstanceFromLoadBalancer = function deregisterInstanceFromLoadBalancer() {
      var instance = $scope.instance;
      var loadBalancerNames = instance.loadBalancers.join(' and ');

      var taskMonitor = {
        application: app,
        title: 'Deregistering ' + instance.instanceId + ' from ' + loadBalancerNames
      };

      var submitMethod = function () {
        return instanceWriter.deregisterInstanceFromLoadBalancer(instance, app, {
          cloudProvider: 'cf',
          networkLoadBalancerNames: instance.loadBalancers,
        });
      };

      confirmationModalService.confirm({
        header: 'Really deregister ' + instance.instanceId + ' from ' + loadBalancerNames + '?',
        buttonText: 'Deregister ' + instance.instanceId,
        destructive: true,
        provider: 'cf',
        account: instance.account,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

    this.enableInstanceInDiscovery = function enableInstanceInDiscovery() {
      var instance = $scope.instance;

      var taskMonitor = {
        application: app,
        title: 'Enabling ' + instance.instanceId + ' in discovery'
      };

      var submitMethod = function () {
        return instanceWriter.enableInstanceInDiscovery(instance, app);
      };

      confirmationModalService.confirm({
        header: 'Really enable ' + instance.instanceId + ' in discovery?',
        buttonText: 'Enable ' + instance.instanceId,
        destructive: false,
        account: instance.account,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

    this.disableInstanceInDiscovery = function disableInstanceInDiscovery() {
      var instance = $scope.instance;

      var taskMonitor = {
        application: app,
        title: 'Disabling ' + instance.instanceId + ' in discovery'
      };

      var submitMethod = function () {
        return instanceWriter.disableInstanceInDiscovery(instance, app);
      };

      confirmationModalService.confirm({
        header: 'Really disable ' + instance.instanceId + ' in discovery?',
        buttonText: 'Disable ' + instance.instanceId,
        destructive: true,
        provider: 'cf',
        account: instance.account,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

    this.hasHealthState = function hasHealthState(healthProviderType, state) {
      var instance = $scope.instance;
      return (instance.health.some(function (health) {
        return health.type === healthProviderType && health.state === state;
      })
      );
    };

    retrieveInstance().then(() => {
      // Two things to look out for here:
      //  1. If the retrieveInstance call completes *after* the user has navigated away from the view, there
      //     is no point in subscribing to the autoRefreshStream
      //  2. If this is a standalone instance, there is no application that will refresh
      if (!$scope.$$destroyed && !app.isStandalone) {
        let refreshWatcher = app.autoRefreshStream.subscribe(retrieveInstance);
        $scope.$on('$destroy', () => refreshWatcher.dispose());
      }
    });

    $scope.account = instance.account;

  }
);
