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

    this.showYaml = function showYaml() {
      $scope.userDataModalTitle = 'Pod YAML';
      $scope.userData = $scope.instance.yaml;
      $uibModal.open({
        templateUrl: require('../../../core/serverGroup/details/userData.html'),
        controller: 'CloseableModalCtrl',
        scope: $scope
      });
    };

    function retrieveInstance() {
      var extraData = {};
      var instanceSummary, loadBalancers, account, namespace;
      if (!app.serverGroups) {
        // standalone instance
        instanceSummary = {};
        loadBalancers = [];
        account = instance.account;
        namespace = instance.region;
      } else {
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
        if (!instanceSummary) {
          // perhaps it is in a server group that is part of another application
          app.loadBalancers.data.some(function (loadBalancer) {
            return loadBalancer.instances.some(function (possibleInstance) {
              if (possibleInstance.id === instance.instanceId) {
                instanceSummary = possibleInstance;
                loadBalancers = [loadBalancer.name];
                account = loadBalancer.account;
                namespace = loadBalancer.region;
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
                    namespace = loadBalancer.region;
                    return true;
                  }
                });
              });
            });
          }
        }
      }

      if (instanceSummary && account && namespace) {
        extraData.account = account;
        extraData.namespace = namespace;
        recentHistoryService.addExtraDataToLatest('instances', extraData);
        return instanceReader.getInstanceDetails(account, namespace, instance.instanceId).then(function(details) {
          details = details.plain();
          $scope.state.loading = false;
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
        let params = {cloudProvider: 'kubernetes'};

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
        provider: 'kubernetes',
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

    this.registerInstanceWithLoadBalancer = function registerInstanceWithLoadBalancer() {
      var instance = $scope.instance;
      var loadBalancerNames = instance.loadBalancers.join(' and ');

      var taskMonitor = {
        application: app,
        title: 'Registering ' + instance.name + ' with ' + loadBalancerNames
      };

      var submitMethod = function () {
        return instanceWriter.registerInstanceWithLoadBalancer(instance, app, { interestingHealthProviderNames: ['Kubernetes'] } );
      };

      confirmationModalService.confirm({
        header: 'Really register ' + instance.name + ' with ' + loadBalancerNames + '?',
        buttonText: 'Register ' + instance.name,
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
        title: 'Deregistering ' + instance.name + ' from ' + loadBalancerNames
      };

      var submitMethod = function () {
        return instanceWriter.deregisterInstanceFromLoadBalancer(instance, app, { interestingHealthProviderNames: ['Kubernetes'] } );
      };

      confirmationModalService.confirm({
        header: 'Really deregister ' + instance.name + ' from ' + loadBalancerNames + '?',
        buttonText: 'Deregister ' + instance.name,
        provider: 'kubernetes',
        account: instance.account,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

    this.canRegisterWithLoadBalancer = function() {
      var instance = $scope.instance;
      if (!instance.loadBalancers || !instance.loadBalancers.length) {
        return false;
      }
      return instance.health.some(function(health) {
        return health.state === 'OutOfService';
      });
    };

    this.canDeregisterFromLoadBalancer = function() {
      var instance = $scope.instance;
      if (!instance.loadBalancers || !instance.loadBalancers.length) {
        return false;
      }
      return instance.healthState !== 'OutOfService';
    };

    this.hasHealthState = function hasHealthState(healthProviderType, state) {
      var instance = $scope.instance;
      return (instance.health.some(function (health) {
        return health.type === healthProviderType && health.state === state;
      })
      );
    };

    let initialize = app.isStandalone ?
      retrieveInstance() :
      $q.all([app.serverGroups.ready(), app.loadBalancers.ready()]).then(retrieveInstance);

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
  }
);
