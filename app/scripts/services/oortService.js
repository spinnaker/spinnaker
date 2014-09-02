'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('oortService', function (searchService, settings, $q, Restangular, _, $timeout, clusterService, loadBalancerService, pond) {

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
          }, 30000);
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

    }

    function deepCopyApplication(original, newApplication) {
      original.accounts = newApplication.accounts;
      original.clusters = newApplication.clusters;
      original.loadBalancers = newApplication.loadBalancers;
      // TODO: this is leaky
    }

    function getApplication(applicationName) {
      var applicationLoader = getApplicationEndpoint(applicationName).get();
      return applicationLoader.then(function(application) {
        addMethodsToApplication(application);
        application.accounts = Object.keys(application.clusters);
        var clusterLoader = clusterService.loadClusters(application).then(function(clusters) {
          application.clusters = clusters;
        });
        var loadBalancerLoader = loadBalancerService.loadLoadBalancers(application).then(function(loadBalancers) {
          application.loadBalancers = loadBalancers;
        });
        var taskLoader = pond.one('applications', applicationName)
          .all('tasks')
          .getList()
          .then(function(data) {
            application.tasks = data;
          });

        return $q.all([clusterLoader, loadBalancerLoader, taskLoader]).then(function() {
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
