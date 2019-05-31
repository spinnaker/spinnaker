'use strict';

const angular = require('angular');
import _ from 'lodash';

import {
  CloudProviderRegistry,
  CONFIRMATION_MODAL_SERVICE,
  InstanceReader,
  INSTANCE_WRITE_SERVICE,
  InstanceTemplates,
  RecentHistoryService,
} from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.azure.instance.detail.controller', [
    require('@uirouter/angularjs').default,
    require('angular-ui-bootstrap'),
    INSTANCE_WRITE_SERVICE,
    CONFIRMATION_MODAL_SERVICE,
  ])
  .controller('azureInstanceDetailsCtrl', [
    '$scope',
    '$state',
    '$uibModal',
    'instanceWriter',
    'confirmationModalService',
    'instance',
    'app',
    '$q',
    function($scope, $state, $uibModal, instanceWriter, confirmationModalService, instance, app, $q) {
      // needed for standalone instances
      $scope.detailsTemplateUrl = CloudProviderRegistry.getValue('azure', 'instance.detailsTemplateUrl');

      $scope.state = {
        loading: true,
        standalone: app.isStandalone,
      };

      function extractHealthMetrics(instance, latest) {
        // do not backfill on standalone instances
        if (app.isStandalone) {
          instance.health = latest.health;
        }

        instance.health = instance.health || [];
        var displayableMetrics = instance.health.filter(function(metric) {
          return metric.type !== 'Azure' || metric.state !== 'Unknown';
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
        var instanceSummary, loadBalancers, account, region, vpcId;
        if (!app.serverGroups) {
          // standalone instance
          instanceSummary = {};
          loadBalancers = [];
          account = instance.account;
          region = instance.region;
        } else {
          app.serverGroups.data.some(function(serverGroup) {
            return serverGroup.instances.some(function(possibleInstance) {
              if (possibleInstance.id === instance.instanceId) {
                instanceSummary = possibleInstance;
                loadBalancers = serverGroup.loadBalancers;
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
              return loadBalancer.instances.some(function(possibleInstance) {
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
              app.loadBalancers.data.some(function(loadBalancer) {
                return loadBalancer.serverGroups.some(function(serverGroup) {
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
              });
            }
          }
        }

        if (instanceSummary && account && region) {
          extraData.account = account;
          extraData.region = region;
          RecentHistoryService.addExtraDataToLatest('instances', extraData);
          return InstanceReader.getInstanceDetails(account, region, instance.instanceId).then(
            function(details) {
              $scope.state.loading = false;
              extractHealthMetrics(instanceSummary, details);
              $scope.instance = _.defaults(details, instanceSummary);
              $scope.instance.account = account;
              $scope.instance.region = region;
              $scope.instance.vpcId = vpcId;
              $scope.instance.loadBalancers = loadBalancers;
              var discoveryMetric = _.find($scope.healthMetrics, function(metric) {
                return metric.type === 'Discovery';
              });
              if (discoveryMetric && discoveryMetric.vipAddress) {
                var vipList = discoveryMetric.vipAddress;
                $scope.instance.vipAddress = vipList.includes(',') ? vipList.split(',') : [vipList];
              }
              $scope.baseIpAddress = details.publicDnsName || details.privateIpAddress;
            },
            function() {
              // When an instance is first starting up, we may not have the details cached in oort yet, but we still
              // want to let the user see what details we have
              $scope.state.loading = false;
              $state.go('^');
            },
          );
        }

        if (!instanceSummary) {
          $scope.instanceIdNotFound = instance.instanceId;
          $scope.state.loading = false;
        }

        return $q.when(null);
      }

      this.canDeregisterFromLoadBalancer = function() {
        return $scope.instance.health.some(function(health) {
          return health.type === 'LoadBalancer';
        });
      };

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
          onTaskComplete: function() {
            if ($state.includes('**.instanceDetails', { instanceId: instance.instanceId })) {
              $state.go('^');
            }
          },
        };

        var submitMethod = function() {
          return instanceWriter.terminateInstance(instance, app);
        };

        confirmationModalService.confirm({
          header: 'Really terminate ' + instance.instanceId + '?',
          buttonText: 'Terminate ' + instance.instanceId,
          account: instance.account,
          provider: 'azure',
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
          return instanceWriter.terminateInstanceAndShrinkServerGroup(instance, app);
        };

        confirmationModalService.confirm({
          header: 'Really terminate ' + instance.instanceId + ' and shrink ' + instance.serverGroup + '?',
          buttonText: 'Terminate ' + instance.instanceId + ' and shrink ' + instance.serverGroup,
          account: instance.account,
          provider: 'azure',
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod,
        });
      };

      this.rebootInstance = function rebootInstance() {
        var instance = $scope.instance;

        var taskMonitor = {
          application: app,
          title: 'Rebooting ' + instance.instanceId,
        };

        var submitMethod = function() {
          return instanceWriter.rebootInstance(instance, app);
        };

        confirmationModalService.confirm({
          header: 'Really reboot ' + instance.instanceId + '?',
          buttonText: 'Reboot ' + instance.instanceId,
          account: instance.account,
          provider: 'azure',
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod,
        });
      };

      this.registerInstanceWithLoadBalancer = function registerInstanceWithLoadBalancer() {
        var instance = $scope.instance;
        var loadBalancerNames = instance.loadBalancers.join(' and ');

        var taskMonitor = {
          application: app,
          title: 'Registering ' + instance.instanceId + ' with ' + loadBalancerNames,
        };

        var submitMethod = function() {
          return instanceWriter.registerInstanceWithLoadBalancer(instance, app);
        };

        confirmationModalService.confirm({
          header: 'Really register ' + instance.instanceId + ' with ' + loadBalancerNames + '?',
          buttonText: 'Register ' + instance.instanceId,
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
          title: 'Deregistering ' + instance.instanceId + ' from ' + loadBalancerNames,
        };

        var submitMethod = function() {
          return instanceWriter.deregisterInstanceFromLoadBalancer(instance, app);
        };

        confirmationModalService.confirm({
          header: 'Really deregister ' + instance.instanceId + ' from ' + loadBalancerNames + '?',
          buttonText: 'Deregister ' + instance.instanceId,
          provider: 'azure',
          account: instance.account,
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
          return instanceWriter.enableInstanceInDiscovery(instance, app);
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
          return instanceWriter.disableInstanceInDiscovery(instance, app);
        };

        confirmationModalService.confirm({
          header: 'Really disable ' + instance.instanceId + ' in discovery?',
          buttonText: 'Disable ' + instance.instanceId,
          provider: 'azure',
          account: instance.account,
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod,
        });
      };

      this.showConsoleOutput = function() {
        $uibModal.open({
          templateUrl: InstanceTemplates.consoleOutputModal,
          controller: 'ConsoleOutputCtrl as ctrl',
          size: 'lg',
          resolve: {
            instance: function() {
              return $scope.instance;
            },
          },
        });
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
