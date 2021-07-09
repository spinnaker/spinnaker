'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { module } from 'angular';
import ANGULAR_UI_BOOTSTRAP from 'angular-ui-bootstrap';
import _ from 'lodash';

import {
  CloudProviderRegistry,
  ConfirmationModalService,
  FirewallLabels,
  InstanceReader,
  InstanceWriter,
  RecentHistoryService,
} from '@spinnaker/core';
import { GOOGLE_COMMON_XPNNAMING_GCE_SERVICE } from '../../common/xpnNaming.gce.service';
import { GCE_HTTP_LOAD_BALANCER_UTILS } from '../../loadBalancer/httpLoadBalancerUtils.service';

export const GOOGLE_INSTANCE_DETAILS_INSTANCE_DETAILS_CONTROLLER = 'spinnaker.instance.detail.gce.controller';
export const name = GOOGLE_INSTANCE_DETAILS_INSTANCE_DETAILS_CONTROLLER; // for backwards compatibility
module(GOOGLE_INSTANCE_DETAILS_INSTANCE_DETAILS_CONTROLLER, [
  UIROUTER_ANGULARJS,
  ANGULAR_UI_BOOTSTRAP,
  GOOGLE_COMMON_XPNNAMING_GCE_SERVICE,
  GCE_HTTP_LOAD_BALANCER_UTILS,
]).controller('gceInstanceDetailsCtrl', [
  '$scope',
  '$state',
  '$uibModal',
  'instance',
  'app',
  'moniker',
  'environment',
  '$q',
  'gceHttpLoadBalancerUtils',
  'gceXpnNamingService',
  function (
    $scope,
    $state,
    $uibModal,
    instance,
    app,
    moniker,
    environment,
    $q,
    gceHttpLoadBalancerUtils,
    gceXpnNamingService,
  ) {
    // needed for standalone instances
    $scope.detailsTemplateUrl = CloudProviderRegistry.getValue('gce', 'instance.detailsTemplateUrl');

    $scope.firewallsLabel = FirewallLabels.get('Firewalls');

    $scope.state = {
      loading: true,
      standalone: app.isStandalone,
    };

    $scope.application = app;
    $scope.moniker = moniker;
    $scope.environment = environment;

    function extractHealthMetrics(instance, latest) {
      // do not backfill on standalone instances
      if (app.isStandalone) {
        instance.health = latest.health;
      }

      instance.health = instance.health || [];
      const displayableMetrics = instance.health.filter(function (metric) {
        return metric.type !== 'Google' || metric.state !== 'Unknown';
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
              extraData.serverGroup = serverGroup.name;
              return true;
            }
          });
        });
        if (!instanceSummary) {
          // perhaps it is in a server group that is part of another application
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
        return InstanceReader.getInstanceDetails(account, region, instance.instanceId).then(function (details) {
          $scope.state.loading = false;
          extractHealthMetrics(instanceSummary, details);
          $scope.instance = _.defaults(details, instanceSummary);
          $scope.instance.account = account;
          $scope.instance.region = region;
          $scope.instance.vpcId = vpcId;
          if (app.getDataSource('loadBalancers')) {
            $scope.instance.loadBalancers = gceHttpLoadBalancerUtils.normalizeLoadBalancerNamesForAccount(
              loadBalancers,
              account,
              app.getDataSource('loadBalancers').data,
            );
          }

          $scope.instance.internalDnsName = $scope.instance.instanceId;
          $scope.instance.internalIpAddress = $scope.instance.networkInterfaces[0].networkIP;

          if ($scope.instance.networkInterfaces[0].accessConfigs) {
            $scope.instance.externalIpAddress = $scope.instance.networkInterfaces[0].accessConfigs[0].natIP;
          }

          $scope.baseIpAddress = $scope.instance.externalIpAddress || $scope.instance.internalIpAddress;

          const projectId = gceXpnNamingService.deriveProjectId($scope.instance);
          $scope.instance.logsLink =
            'https://console.developers.google.com/project/' +
            projectId +
            '/logs?service=gce_instance&minLogLevel=0&filters=text:' +
            $scope.instance.instanceId;

          $scope.instance.network = getNetwork(projectId);
          $scope.instance.subnet = getSubnet(projectId);

          $scope.instance.sshLink =
            $scope.instance.selfLink.replace(
              /www.googleapis.com\/compute\/(alpha|beta|v1)/,
              'cloudssh.developers.google.com',
            ) + '?authuser=0&hl=en_US';

          $scope.instance.gcloudSSHCommand =
            'gcloud compute ssh --project ' +
            projectId +
            ' --zone ' +
            $scope.instance.placement.availabilityZone +
            ' ' +
            instance.instanceId;

          augmentTagsWithHelp();
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

    function augmentTagsWithHelp() {
      if (_.has($scope, 'instance.tags.items') && _.has($scope, 'instance.securityGroups')) {
        const securityGroups = _.chain($scope.instance.securityGroups)
          .map((securityGroup) => {
            return _.find(app.securityGroups.data, {
              accountName: $scope.instance.account,
              region: 'global',
              id: securityGroup.groupId,
            });
          })
          .compact()
          .value();

        const helpMap = {};

        $scope.instance.tags.items.forEach((tag) => {
          const securityGroupsMatches = _.filter(securityGroups, (securityGroup) =>
            _.includes(securityGroup.targetTags, tag),
          );
          const securityGroupMatchNames = _.map(securityGroupsMatches, 'name');

          if (!_.isEmpty(securityGroupMatchNames)) {
            const groupOrGroups = securityGroupMatchNames.length > 1 ? 'groups' : 'group';

            helpMap[tag] =
              'This tag associates this instance with security ' +
              groupOrGroups +
              ' <em>' +
              securityGroupMatchNames.join(', ') +
              '</em>.';
          }
        });

        $scope.instance.tags.helpMap = helpMap;
      }
    }

    function getNetwork(projectId) {
      const networkUrl = _.get($scope.instance, 'networkInterfaces[0].network');
      return gceXpnNamingService.decorateXpnResourceIfNecessary(projectId, networkUrl);
    }

    function getSubnet(projectId) {
      const subnetUrl = _.get($scope.instance, 'networkInterfaces[0].subnetwork');
      return gceXpnNamingService.decorateXpnResourceIfNecessary(projectId, subnetUrl);
    }

    this.canRegisterWithLoadBalancer = function () {
      const instance = $scope.instance;
      const instanceLoadBalancerDoesNotSupportRegister =
        !app.loadBalancers ||
        _.chain(app.loadBalancers.data)
          .filter((lb) => lb.loadBalancerType !== 'NETWORK' && lb.account === instance.account)
          .map('name')
          .intersection(instance.loadBalancers || [])
          .value().length;

      if (!instance.loadBalancers || !instance.loadBalancers.length || instanceLoadBalancerDoesNotSupportRegister) {
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

    this.canDeregisterFromLoadBalancer = function () {
      const instance = $scope.instance;
      const instanceLoadBalancerDoesNotSupportDeregister =
        !app.loadBalancers ||
        _.chain(app.loadBalancers.data)
          .filter((lb) => lb.loadBalancerType !== 'NETWORK' && lb.account === instance.account)
          .map('name')
          .intersection(instance.loadBalancers || [])
          .value().length;

      if (!instance.loadBalancers || !instance.loadBalancers.length || instanceLoadBalancerDoesNotSupportDeregister) {
        return false;
      }
      const hasLoadBalancerHealth = instance.health.some(function (health) {
        return health.type === 'LoadBalancer';
      });
      return hasLoadBalancerHealth;
    };

    this.canRegisterWithDiscovery = function () {
      const instance = $scope.instance;
      const discoveryHealth = instance.health.filter(function (health) {
        return health.type === 'Discovery';
      });
      return discoveryHealth.length ? discoveryHealth[0].state === 'OutOfService' : false;
    };

    this.showInstanceActionsDivider = function () {
      return (
        this.canRegisterWithDiscovery() ||
        this.hasHealthState('Discovery', 'Up') ||
        this.canRegisterWithLoadBalancer() ||
        this.canDeregisterFromLoadBalancer()
      );
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
        const params = { cloudProvider: 'gce' };

        if (instance.serverGroup) {
          params.managedInstanceGroupName = instance.serverGroup;
        }

        return InstanceWriter.terminateInstance(instance, app, params);
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
        return InstanceWriter.terminateInstanceAndShrinkServerGroup(instance, app, {
          serverGroupName: instance.serverGroup,
          instanceIds: [instance.instanceId],
          zone: instance.placement.availabilityZone,
        });
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
        return InstanceWriter.rebootInstance(instance, app, {
          // We can't really reliably do anything other than ignore health here.
          interestingHealthProviderNames: [],
        });
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
]);
