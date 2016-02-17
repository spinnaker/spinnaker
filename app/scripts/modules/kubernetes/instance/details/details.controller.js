'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.instance.detail.kubernetes.controller', [
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
  .controller('kubernetesInstanceDetailsController', function ($scope, $state, $uibModal, InsightFilterStateModel,
                                                               instanceWriter, confirmationModalService, recentHistoryService,
                                                               cloudProviderRegistry, instanceReader, _, instance, app, $q) {
    // needed for standalone instances
    $scope.detailsTemplateUrl = cloudProviderRegistry.getValue('kubernetes', 'instance.detailsTemplateUrl');

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
          return metric.type !== 'Kubernetes' || metric.state !== 'Unknown';
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
      var instanceSummary, loadBalancers, account, namespace;
      app.serverGroups.data.some(function (serverGroup) {
        return serverGroup.instances.some(function (possibleInstance) {
          if (possibleInstance.id === instance.instanceId) {
            instanceSummary = possibleInstance;
            loadBalancers = serverGroup.loadBalancers;
            account = serverGroup.account;
            namespace = serverGroup.region;
            extraData.serverGroup = serverGroup.name;
            return true;
          }
        });
      });

      if (instanceSummary && account && namespace) {
        extraData.account = account;
        extraData.namespace = namespace;
        recentHistoryService.addExtraDataToLatest('instances', extraData);
        return instanceReader.getInstanceDetails(account, namespace, instance.instanceId).then(function(details) {
          details = details.plain();
          $scope.state.loading = false;
          extractHealthMetrics(instanceSummary, details);
          $scope.instance = _.defaults(details, instanceSummary);
          $scope.instance.account = account;
          $scope.instance.namespace = namespace;
          $scope.instance.region = namespace;
          $scope.instance.loadBalancers = loadBalancers;
          var pod = $scope.instance.pod;
          $scope.instance.dnsPolicy = pod.spec.dnsPolicy;
          $scope.instance.apiVersion = pod.apiVersion;
          $scope.instance.kind = pod.kind;
          $scope.instance.nodeName = pod.spec.nodeName;
          $scope.instance.restartPolicy = pod.spec.restartPolicy;
          $scope.instance.terminationGracePeriodSeconds = pod.spec.terminationGracePeriodSeconds;
          $scope.instance.hostIp = pod.status.hostIP;
          $scope.instance.podIp = pod.status.podIP;
          $scope.instance.phase = pod.status.phase;
          $scope.instance.volumes = pod.spec.volumes;
          $scope.instance.metadata = pod.metadata;
          $scope.instance.imagePullSecrets = pod.spec.imagePullSecrets;
          $scope.instance.containers = pod.spec.containers;
          $scope.instance.containerStatuses = pod.status.containerStatuses;
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
    };

    this.canDeregisterFromLoadBalancer = function() {
    };

    this.canRegisterWithDiscovery = function() {
    };

    this.terminateInstance = function terminateInstance() {
    };

    this.terminateInstanceAndShrinkServerGroup = function terminateInstanceAndShrinkServerGroup() {
    };

    this.rebootInstance = function rebootInstance() {
    };

    this.registerInstanceWithLoadBalancer = function registerInstanceWithLoadBalancer() {
    };

    this.deregisterInstanceFromLoadBalancer = function deregisterInstanceFromLoadBalancer() {
    };

    this.enableInstanceInDiscovery = function enableInstanceInDiscovery() {
    };

    this.disableInstanceInDiscovery = function disableInstanceInDiscovery() {
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
      //     is no point in subscribing to the refresh
      //  2. If this is a standalone instance, there is no application that will refresh
      if (!$scope.$$destroyed && !app.isStandalone) {
        app.serverGroups.onRefresh(retrieveInstance);
      }
    });

    $scope.account = instance.account;

  }
);
