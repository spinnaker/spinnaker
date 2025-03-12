'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { module } from 'angular';
import ANGULAR_UI_BOOTSTRAP from 'angular-ui-bootstrap';
import _ from 'lodash';

import {
  CloudProviderRegistry,
  ConfirmationModalService,
  InstanceReader,
  InstanceWriter,
  RecentHistoryService,
} from '@spinnaker/core';

export const AZURE_INSTANCE_DETAILS_INSTANCE_DETAILS_CONTROLLER = 'spinnaker.azure.instance.detail.controller';
export const name = AZURE_INSTANCE_DETAILS_INSTANCE_DETAILS_CONTROLLER; // for backwards compatibility
module(AZURE_INSTANCE_DETAILS_INSTANCE_DETAILS_CONTROLLER, [UIROUTER_ANGULARJS, ANGULAR_UI_BOOTSTRAP]).controller(
  'azureInstanceDetailsCtrl',
  [
    '$scope',
    '$state',
    '$uibModal',
    'instance',
    'app',
    '$q',
    function ($scope, $state, $uibModal, instance, app, $q) {
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
        const displayableMetrics = instance.health.filter(function (metric) {
          return metric.type !== 'Azure' || metric.state !== 'Unknown';
        });
        // backfill details where applicable
        if (latest.health) {
          displayableMetrics.forEach(function (metric) {
            const detailsMatch = latest.health.filter(function (latestHealth) {
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
        const extraData = {};
        let instanceSummary, loadBalancers, account, region, vpcId;
        if (!app.serverGroups) {
          // standalone instance
          instanceSummary = {};
          loadBalancers = [];
          account = instance.account;
          region = instance.region;
        } else {
          app.serverGroups.data.some(function (serverGroup) {
            return serverGroup.instances.some(function (possibleInstance) {
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
            app.loadBalancers.data.some(function (loadBalancer) {
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
              app.loadBalancers.data.some(function (loadBalancer) {
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
          RecentHistoryService.addExtraDataToLatest('instances', extraData);
          return InstanceReader.getInstanceDetails(account, region, instance.instanceId).then(
            function (details) {
              $scope.state.loading = false;
              extractHealthMetrics(instanceSummary, details);
              $scope.instance = _.defaults(details, instanceSummary);
              $scope.instance.account = account;
              $scope.instance.region = region;
              $scope.instance.vpcId = vpcId;
              $scope.instance.loadBalancers = loadBalancers;
              const discoveryMetric = _.find($scope.healthMetrics, function (metric) {
                return metric.type === 'Discovery';
              });
              if (discoveryMetric && discoveryMetric.vipAddress) {
                const vipList = discoveryMetric.vipAddress;
                $scope.instance.vipAddress = vipList.includes(',') ? vipList.split(',') : [vipList];
              }
              $scope.baseIpAddress = details.publicDnsName || details.privateIpAddress;
            },
            function () {
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

      this.canDeregisterFromLoadBalancer = function () {
        return $scope.instance.health.some(function (health) {
          return health.type === 'LoadBalancer';
        });
      };

      this.canRegisterWithLoadBalancer = function () {
        const instance = $scope.instance;
        if (!instance.loadBalancers || !instance.loadBalancers.length) {
          return false;
        }
        const outOfService = instance.health.some(function (health) {
          return health.type === 'LoadBalancer' && health.state === 'OutOfService';
        });
        const hasLoadBalancerHealth = instance.health.some(function (health) {
          return health.type === 'LoadBalancer';
        });
        return outOfService || !hasLoadBalancerHealth;
      };

      this.canRegisterWithDiscovery = function () {
        const instance = $scope.instance;
        const discoveryHealth = instance.health.filter(function (health) {
          return health.type === 'Discovery';
        });
        return discoveryHealth.length ? discoveryHealth[0].state === 'OutOfService' : false;
      };

      this.terminateInstance = function terminateInstance() {
        const instance = $scope.instance;

        const taskMonitor = {
          application: app,
          title: 'Terminating ' + instance.instanceId,
          onTaskComplete: function () {
            if ($state.includes('**.instanceDetails', { instanceId: instance.instanceId })) {
              $state.go('^');
            }
          },
        };

        const submitMethod = function () {
          return InstanceWriter.terminateInstance(instance, app);
        };

        ConfirmationModalService.confirm({
          header: 'Really terminate ' + instance.instanceId + '?',
          buttonText: 'Terminate ' + instance.instanceId,
          account: instance.account,
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod,
        });
      };

      this.terminateInstanceAndShrinkServerGroup = function terminateInstanceAndShrinkServerGroup() {
        const instance = $scope.instance;

        const taskMonitor = {
          application: app,
          title: 'Terminating ' + instance.instanceId + ' and shrinking server group',
          onTaskComplete: function () {
            if ($state.includes('**.instanceDetails', { instanceId: instance.instanceId })) {
              $state.go('^');
            }
          },
        };

        const submitMethod = function () {
          return InstanceWriter.terminateInstanceAndShrinkServerGroup(instance, app);
        };

        ConfirmationModalService.confirm({
          header: 'Really terminate ' + instance.instanceId + ' and shrink ' + instance.serverGroup + '?',
          buttonText: 'Terminate ' + instance.instanceId + ' and shrink ' + instance.serverGroup,
          account: instance.account,
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod,
        });
      };

      this.rebootInstance = function rebootInstance() {
        const instance = $scope.instance;

        const taskMonitor = {
          application: app,
          title: 'Rebooting ' + instance.instanceId,
        };

        const submitMethod = function () {
          return InstanceWriter.rebootInstance(instance, app);
        };

        ConfirmationModalService.confirm({
          header: 'Really reboot ' + instance.instanceId + '?',
          buttonText: 'Reboot ' + instance.instanceId,
          account: instance.account,
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod,
        });
      };

      this.registerInstanceWithLoadBalancer = function registerInstanceWithLoadBalancer() {
        const instance = $scope.instance;
        const loadBalancerNames = instance.loadBalancers.join(' and ');

        const taskMonitor = {
          application: app,
          title: 'Registering ' + instance.instanceId + ' with ' + loadBalancerNames,
        };

        const submitMethod = function () {
          return InstanceWriter.registerInstanceWithLoadBalancer(instance, app);
        };

        ConfirmationModalService.confirm({
          header: 'Really register ' + instance.instanceId + ' with ' + loadBalancerNames + '?',
          buttonText: 'Register ' + instance.instanceId,
          account: instance.account,
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod,
        });
      };

      this.deregisterInstanceFromLoadBalancer = function deregisterInstanceFromLoadBalancer() {
        const instance = $scope.instance;
        const loadBalancerNames = instance.loadBalancers.join(' and ');

        const taskMonitor = {
          application: app,
          title: 'Deregistering ' + instance.instanceId + ' from ' + loadBalancerNames,
        };

        const submitMethod = function () {
          return InstanceWriter.deregisterInstanceFromLoadBalancer(instance, app);
        };

        ConfirmationModalService.confirm({
          header: 'Really deregister ' + instance.instanceId + ' from ' + loadBalancerNames + '?',
          buttonText: 'Deregister ' + instance.instanceId,
          account: instance.account,
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod,
        });
      };

      this.enableInstanceInDiscovery = function enableInstanceInDiscovery() {
        const instance = $scope.instance;

        const taskMonitor = {
          application: app,
          title: 'Enabling ' + instance.instanceId + ' in discovery',
        };

        const submitMethod = function () {
          return InstanceWriter.enableInstanceInDiscovery(instance, app);
        };

        ConfirmationModalService.confirm({
          header: 'Really enable ' + instance.instanceId + ' in discovery?',
          buttonText: 'Enable ' + instance.instanceId,
          account: instance.account,
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod,
        });
      };

      this.disableInstanceInDiscovery = function disableInstanceInDiscovery() {
        const instance = $scope.instance;

        const taskMonitor = {
          application: app,
          title: 'Disabling ' + instance.instanceId + ' in discovery',
        };

        const submitMethod = function () {
          return InstanceWriter.disableInstanceInDiscovery(instance, app);
        };

        ConfirmationModalService.confirm({
          header: 'Really disable ' + instance.instanceId + ' in discovery?',
          buttonText: 'Disable ' + instance.instanceId,
          account: instance.account,
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod,
        });
      };

      this.hasHealthState = function hasHealthState(healthProviderType, state) {
        const instance = $scope.instance;
        return instance.health.some(function (health) {
          return health.type === healthProviderType && health.state === state;
        });
      };

      const initialize = app.isStandalone
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
  ],
);
