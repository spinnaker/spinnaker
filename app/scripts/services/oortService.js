'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('oortService', function (searchService, settings, $q, Restangular, _, $timeout, clusterService, loadBalancerService) {

    var oortEndpoint = Restangular.withConfig(function(RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.oortUrl);
    });

    function listApplications() {
      return oortEndpoint.all('applications').getList();
    }

    function getApplicationEndpoint(application) {
      return oortEndpoint.one('applications', application);
    }

    function addMethodsToApplication(application) {

      application.disableAutoRefresh = function disableAutoRefresh() {
        application.autoRefreshEnabled = false;
      };

      application.enableAutoRefresh = function enableAutoRefresh(scope) {
        application.autoRefreshEnabled = true;
        autoRefresh(scope);
      };

      function autoRefresh(scope) {
        application.onAutoRefresh = application.onAutoRefresh || angular.noop;
        if (application.autoRefreshEnabled) {
          var timeout = $timeout(function () {
            getApplication(application.name).then(function (newApplication) {
              deepCopyApplication(application, newApplication);
              application.onAutoRefresh();
              autoRefresh(scope);
            });
          }, 3000);
          scope.$on('$destroy', function () {
            $timeout.cancel(timeout);
          });
        }
      }

      application.getCluster = function getCluster(accountName, clusterName) {
        var matches = application.clusters.filter(function (cluster) {
          return cluster.name === clusterName && cluster.account === accountName;
        });
        return matches.length ? matches[0] : null;
      };
      application.getServerGroups = function getServerGroups() {
        return _.flatten(_.pluck(application.clusters, 'serverGroups'));
      };

      application.serverGroupIsInLoadBalancer = function serverGroupIsInLoadBalancer(serverGroup, loadBalancer) {
        if (serverGroup.region !== loadBalancer.region || loadBalancer.serverGroups.indexOf(serverGroup.name) === -1) {
          return false;
        }
        // only include if load balancer is fronting an instance
        var elbInstanceIds = _.pluck(loadBalancer.elb.instances, 'instanceId'),
          serverGroupInstanceIds = _.pluck(serverGroup.instances, 'instanceId');
        return elbInstanceIds.some(function (elbInstanceId) {
          return serverGroupInstanceIds.indexOf(elbInstanceId) !== -1;
        });
      };

    }

    function deepCopyApplication(original, newApplication) {
      original.accounts = newApplication.accounts;
      original.clusters = newApplication.clusters;
      original.loadBalancers = newApplication.loadBalancers;
      delete newApplication.accounts;
      delete newApplication.clusters;
      delete newApplication.loadBalancers;
    }

    function getApplication(applicationName) {
      var applicationLoader = getApplicationEndpoint(applicationName).get();
      return applicationLoader.then(function(application) {
        addMethodsToApplication(application);
        application.accounts = Object.keys(application.clusters);
        var clusterLoader = clusterService.loadClusters(application);
        var loadBalancerLoader = loadBalancerService.loadLoadBalancers(application);

        return $q.all([clusterLoader, loadBalancerLoader]).then(function() {
          loadBalancerService.normalizeLoadBalancersWithServerGroups(application);
          clusterService.normalizeServerGroupsWithLoadBalancers(application);
          return application;
        });
      });
    }

    function findAmis(applicationName) {
      return searchService.search({q: applicationName, type: 'namedImages'}).then(function(results) {
        return results.data[0].results;
      });
    }

    return {
      listApplications: listApplications,
      getApplication: getApplication,
      findAmis: findAmis
    };
  });
