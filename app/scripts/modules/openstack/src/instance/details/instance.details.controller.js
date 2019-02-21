'use strict';

const angular = require('angular');
import _ from 'lodash';

import {
  CloudProviderRegistry,
  CONFIRMATION_MODAL_SERVICE,
  FirewallLabels,
  InstanceReader,
  INSTANCE_WRITE_SERVICE,
  RecentHistoryService,
  SETTINGS,
} from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.instance.detail.openstack.controller', [
    require('@uirouter/angularjs').default,
    require('angular-ui-bootstrap'),
    INSTANCE_WRITE_SERVICE,
    CONFIRMATION_MODAL_SERVICE,
  ])
  .controller('openstackInstanceDetailsCtrl', [
    '$scope',
    '$state',
    '$uibModal',
    'instanceWriter',
    'confirmationModalService',
    'instance',
    'app',
    'moniker',
    'environment',
    '$q',
    'overrides',
    function(
      $scope,
      $state,
      $uibModal,
      instanceWriter,
      confirmationModalService,
      instance,
      app,
      moniker,
      environment,
      $q,
      overrides,
    ) {
      // needed for standalone instances
      $scope.detailsTemplateUrl = CloudProviderRegistry.getValue('openstack', 'instance.detailsTemplateUrl');

      $scope.state = {
        loading: true,
        standalone: app.isStandalone,
        instancePort: _.get(app, 'attributes.instancePort') || SETTINGS.defaultInstancePort || 80,
      };

      $scope.firewallsLabel = FirewallLabels.get('Firewalls');

      $scope.application = app;
      $scope.moniker = moniker;
      $scope.environment = environment;

      function extractHealthMetrics(instance, latest) {
        // do not backfill on standalone instances
        if (app.isStandalone) {
          instance.health = latest.health;
        }

        instance.health = instance.health || [];
        var displayableMetrics = instance.health.filter(function(metric) {
          return metric.type !== 'Openstack' || metric.state !== 'Unknown';
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
        var instanceSummary, loadBalancers, loadBalancerIds, account, region;
        if (!app.serverGroups) {
          // standalone instance
          instanceSummary = {};
          loadBalancers = [];
          loadBalancerIds = [];
          account = instance.account;
          region = instance.region;
        } else {
          app.serverGroups.data.some(function(serverGroup) {
            return serverGroup.instances.some(function(possibleInstance) {
              if (possibleInstance.id === instance.instanceId) {
                instanceSummary = possibleInstance;
                loadBalancers = serverGroup.loadBalancers;
                loadBalancerIds = serverGroup.loadBalancerIds;
                account = serverGroup.account;
                region = serverGroup.region;
                extraData.serverGroup = serverGroup.name;
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
                  loadBalancerIds = [loadBalancer.id];
                  account = loadBalancer.account;
                  region = loadBalancer.region;
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
                      loadBalancerIds = [loadBalancer.id];
                      account = loadBalancer.account;
                      region = loadBalancer.region;
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
          return InstanceReader.getInstanceDetails(account, region, instance.instanceId).then(details => {
            if ($scope.$$destroyed) {
              return;
            }
            $scope.state.loading = false;
            extractHealthMetrics(instanceSummary, details);
            $scope.instance = _.defaults(details, instanceSummary);
            $scope.instance.account = account;
            $scope.instance.region = region;
            $scope.instance.loadBalancers = loadBalancers;
            $scope.instance.loadBalancerIds = loadBalancerIds;

            $scope.baseIpAddress = details.ipv4 || details.ipv6;
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
        $state.go('^', { allowModalToStayOpen: true }, { location: 'replace' });
      }

      this.canDeregisterFromLoadBalancer = function() {
        let healthMetrics = $scope.instance.health || [];
        return healthMetrics.some(function(health) {
          return health.type === 'LoadBalancer';
        });
      };

      this.canRegisterWithLoadBalancer = function() {
        var instance = $scope.instance,
          healthMetrics = instance.health || [];
        if (!instance.loadBalancers || !instance.loadBalancers.length) {
          return false;
        }
        var outOfService = healthMetrics.some(function(health) {
          return health.type === 'LoadBalancer' && health.state === 'OutOfService';
        });
        var hasLoadBalancerHealth = healthMetrics.some(function(health) {
          return health.type === 'LoadBalancer';
        });
        return outOfService || !hasLoadBalancerHealth;
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
          return instanceWriter.terminateInstance(instance, app, {
            cloudProvider: instance.provider,
            serverGroupName: instance.serverGroup,
          });
        };

        confirmationModalService.confirm({
          header: 'Really terminate ' + instance.instanceId + '?',
          buttonText: 'Terminate ' + instance.instanceId,
          account: instance.account,
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

        var submitMethod = (params = {}) => {
          if (app.attributes && app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
            params.interestingHealthProviderNames = ['Openstack'];
          } else {
            params.interestingHealthProviderNames = [];
          }

          return instanceWriter.rebootInstance(instance, app, params);
        };

        confirmationModalService.confirm({
          header: 'Really reboot ' + instance.instanceId + '?',
          buttonText: 'Reboot ' + instance.instanceId,
          account: instance.account,
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

        var submitMethod = (params = {}) => {
          params.loadBalancerIds = instance.loadBalancerIds;
          return instanceWriter.registerInstanceWithLoadBalancer(instance, app, params);
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

        var submitMethod = (params = {}) => {
          params.loadBalancerIds = instance.loadBalancerIds;
          return instanceWriter.deregisterInstanceFromLoadBalancer(instance, app, params);
        };

        confirmationModalService.confirm({
          header: 'Really deregister ' + instance.instanceId + ' from ' + loadBalancerNames + '?',
          buttonText: 'Deregister ' + instance.instanceId,
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
    },
  ]);
