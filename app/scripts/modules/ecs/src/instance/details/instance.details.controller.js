'use strict';

const angular = require('angular');
import _ from 'lodash';

import {
  CloudProviderRegistry,
  CONFIRMATION_MODAL_SERVICE,
  InstanceReader,
  RecentHistoryService,
  SETTINGS,
} from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.ecs.instance.details.controller', [
    require('@uirouter/angularjs').default,
    require('angular-ui-bootstrap'),
    CONFIRMATION_MODAL_SERVICE,
  ])
  .controller('ecsInstanceDetailsCtrl', function(
    $scope,
    $state,
    $uibModal,
    confirmationModalService,
    instanceWriter,
    instance,
    app,
    moniker,
    environment,
    $q,
    overrides,
  ) {
    // needed for standalone instances
    $scope.detailsTemplateUrl = CloudProviderRegistry.getValue('ecs', 'instance.detailsTemplateUrl');

    $scope.state = {
      loading: true,
      standalone: app.isStandalone,
      instancePort: _.get(app, 'attributes.instancePort') || SETTINGS.defaultInstancePort || 80,
    };

    $scope.application = app;
    $scope.moniker = moniker;
    $scope.environment = environment;

    const cloudProvider = 'ecs';
    const defaultRequestParams = { cloudProvider: cloudProvider };

    function extractHealthMetrics(instance, latest) {
      // do not backfill on standalone instances
      if (app.isStandalone) {
        instance.health = latest.health;
      }

      instance.health = instance.health || [];
      var displayableMetrics = instance.health.filter(function(metric) {
        return metric.type !== 'Ecs' || metric.state !== 'Unknown';
      });
      // backfill details where applicable
      if (latest.health) {
        displayableMetrics.forEach(function(metric) {
          var detailsMatch = latest.health.filter(function(latestHealth) {
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
      var instanceSummary, loadBalancers, targetGroup, account, region, vpcId;
      if (!app.serverGroups) {
        // standalone instance
        instanceSummary = {};
        loadBalancers = [];
        targetGroup = [];
        account = instance.account;
        region = instance.region;
      } else {
        app.serverGroups.data.some(function(serverGroup) {
          return serverGroup.instances.some(function(possibleInstance) {
            if (possibleInstance.id === instance.instanceId) {
              instanceSummary = possibleInstance;
              loadBalancers = serverGroup.loadBalancers;
              targetGroup = serverGroup.targetGroup;
              account = serverGroup.account;
              region = serverGroup.region;
              vpcId = serverGroup.vpcId;
              extraData.serverGroup = serverGroup.name;
              extraData.vpcId = serverGroup.vpcId;
              return true;
            }
          });
        });
        if (!instanceSummary) {
          // perhaps it is in a server group that is part of another app
          app.loadBalancers.data.some(function(loadBalancer) {
            return (
              loadBalancer.instances.some(function(possibleInstance) {
                if (possibleInstance.id === instance.instanceId) {
                  instanceSummary = possibleInstance;
                  loadBalancers = [loadBalancer.name];
                  account = loadBalancer.account;
                  region = loadBalancer.region;
                  vpcId = loadBalancer.vpcId;
                  return true;
                }
              }) ||
              loadBalancer.targetGroup.some(function(targetGroup) {
                return targetGroup.instances.some(function(possibleInstance) {
                  if (possibleInstance.id === instance.instanceId) {
                    instanceSummary = possibleInstance;
                    targetGroup = targetGroup.name;
                    account = loadBalancer.account;
                    region = loadBalancer.region;
                    vpcId = loadBalancer.vpcId;
                    return true;
                  }
                });
              })
            );
          });
          if (!instanceSummary) {
            // perhaps it is in a disabled server group via a load balancer
            app.loadBalancers.data.some(function(loadBalancer) {
              return (
                loadBalancer.serverGroups.some(function(serverGroup) {
                  if (!serverGroup.isDisabled) {
                    return false;
                  }
                  return serverGroup.instances.some(function(possibleInstance) {
                    if (possibleInstance.id === instance.instanceId) {
                      instanceSummary = possibleInstance;
                      loadBalancers = [loadBalancer.name];
                      account = loadBalancer.account;
                      region = loadBalancer.region;
                      vpcId = loadBalancer.vpcId;
                      return true;
                    }
                  });
                }) ||
                loadBalancer.targetGroup.some(function(targetGroup) {
                  targetGroup.serverGroups.some(function(serverGroup) {
                    if (!serverGroup.isDisabled) {
                      return false;
                    }
                    return serverGroup.instances.some(function(possibleInstance) {
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
                })
              );
            });
          }
        }
      }

      if (instanceSummary && account && region) {
        extraData.account = account;
        extraData.region = region;
        RecentHistoryService.addExtraDataToLatest('instances', extraData);
        return InstanceReader.getInstanceDetails(account, region, instance.instanceId).then(details => {
          if ($scope.$$destroyed) {
            return;
          }
          $scope.state.loading = false;
          extractHealthMetrics(instanceSummary, details);
          $scope.instance = _.defaults(details, instanceSummary);
          $scope.instance.account = account;
          $scope.instance.region = region;
          $scope.instance.vpcId = vpcId;
          $scope.instance.loadBalancers = loadBalancers;
          $scope.instance.targetGroup = targetGroup;
          if ($scope.instance.networkInterfaces) {
            var permanentNetworkInterfaces = $scope.instance.networkInterfaces.filter(
              f => f.attachment.deleteOnTermination === false,
            );
            if (permanentNetworkInterfaces.length) {
              $scope.instance.permanentIps = permanentNetworkInterfaces.map(f => f.privateIpAddress);
            }
          }
          $scope.baseIpAddress = details.publicDnsName || details.privateIpAddress;
          if (overrides.instanceDetailsLoaded) {
            overrides.instanceDetailsLoaded();
          }
        }, autoClose);
      }

      if (!instanceSummary) {
        $scope.instanceIdNotFound = instance.instanceId;
        $scope.state.loading = false;
      }

      return $q.when(null);
    }

    function autoClose() {
      if ($scope.$$destroyed) {
        return;
      }
      if (app.isStandalone) {
        $scope.state.loading = false;
        $scope.instanceIdNotFound = instance.instanceId;
        $scope.state.notFoundStandalone = true;
        RecentHistoryService.removeLastItem('instances');
      } else {
        $state.go('^', { allowModalToStayOpen: true }, { location: 'replace' });
      }
    }

    this.canRegisterWithDiscovery = function() {
      var instance = $scope.instance;
      let healthMetrics = instance.health || [];
      var discoveryHealth = healthMetrics.filter(function(health) {
        return health.type === 'Discovery';
      });
      return discoveryHealth.length ? discoveryHealth[0].state === 'OutOfService' : false;
    };

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
        return instanceWriter.terminateInstance(instance, app, defaultRequestParams);
      };

      confirmationModalService.confirm({
        header: 'Really terminate ' + instance.instanceId + '?',
        buttonText: 'Terminate ' + instance.instanceId,
        account: instance.account,
        provider: 'ecs',
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod,
      });
    };

    this.terminateInstanceAndShrinkServerGroup = function terminateInstanceAndShrinkServerGroup() {
      var instance = $scope.instance;

      var taskMonitor = {
        application: app,
        title: 'Terminating ' + instance.instanceId + ' and shrinking server group',
        onTaskComplete: function() {
          if ($state.includes('**.instanceDetails', { instanceId: instance.instanceId })) {
            $state.go('^');
          }
        },
      };

      var submitMethod = function() {
        return instanceWriter.terminateInstanceAndShrinkServerGroup(instance, app, defaultRequestParams);
      };

      confirmationModalService.confirm({
        header: 'Really terminate ' + instance.instanceId + ' and shrink ' + instance.serverGroup + '?',
        buttonText: 'Terminate ' + instance.instanceId + ' and shrink ' + instance.serverGroup,
        account: instance.account,
        provider: 'ecs',
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod,
      });
    };

    this.enableInstanceInDiscovery = function enableInstanceInDiscovery() {
      var instance = $scope.instance;

      var taskMonitor = {
        application: app,
        title: 'Enabling ' + instance.instanceId + ' in discovery',
      };

      var submitMethod = function() {
        return instanceWriter.enableInstanceInDiscovery(instance, app, defaultRequestParams);
      };

      confirmationModalService.confirm({
        header: 'Really enable ' + instance.instanceId + ' in discovery?',
        buttonText: 'Enable ' + instance.instanceId,
        account: instance.account,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod,
      });
    };

    this.disableInstanceInDiscovery = function disableInstanceInDiscovery() {
      var instance = $scope.instance;

      var taskMonitor = {
        application: app,
        title: 'Disabling ' + instance.instanceId + ' in discovery',
      };

      var submitMethod = function() {
        return instanceWriter.disableInstanceInDiscovery(instance, app, defaultRequestParams);
      };

      confirmationModalService.confirm({
        header: 'Really disable ' + instance.instanceId + ' in discovery?',
        buttonText: 'Disable ' + instance.instanceId,
        provider: 'ecs',
        account: instance.account,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod,
      });
    };

    this.hasHealthState = function hasHealthState(healthProviderType, state) {
      var instance = $scope.instance,
        healthMetrics = instance.health || [];
      return healthMetrics.some(function(health) {
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
  });
