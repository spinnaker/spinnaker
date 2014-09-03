'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('oortService', function (searchService, settings, $q, Restangular, _, $timeout, clusterService, loadBalancerService, pond) {

    var applicationListEndpoint = Restangular.withConfig(function(RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.oortUrl);
    });

    var oortEndpoint = Restangular.withConfig(function(RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.oortUrl);

      RestangularConfigurer.addElementTransformer('applications', false, function(application) {

        function autoRefresh(scope) {
          application.onAutoRefresh = application.onAutoRefresh || angular.noop;
          if (application.autoRefreshEnabled) {
            var timeout = $timeout(function () {
              getApplication(application.name).then(function (newApplication) {
                deepCopyApplication(application, newApplication);
                application.onAutoRefresh();
                newApplication = null;
                autoRefresh(scope);
              });
            }, 30000);
            scope.$on('$destroy', function () {
              application.disableAutoRefresh();
              $timeout.cancel(timeout);
            });
          }
        }

        application.disableAutoRefresh = function disableAutoRefresh() {
          application.autoRefreshEnabled = false;
        };

        application.enableAutoRefresh = function enableAutoRefresh(scope) {
          application.autoRefreshEnabled = true;
          autoRefresh(scope);
        };

        application.getCluster = function getCluster(accountName, clusterName) {
          var matches = application.clusters.filter(function (cluster) {
            return cluster.name === clusterName && cluster.account === accountName;
          });
          return matches.length ? matches[0] : null;
        };

        if (application.fromServer) {
          application.accounts = Object.keys(application.clusters);
        }
        return application;

      });
    });

    function listApplications() {
      return applicationListEndpoint.all('applications').getList();
    }

    function getApplicationEndpoint(application) {
      return oortEndpoint.one('applications', application);
    }

    function deepCopyApplication(original, newApplication) {
      original.accounts = newApplication.accounts;
      original.clusters = newApplication.clusters;
      original.loadBalancers = newApplication.loadBalancers;
      original.tasks = newApplication.tasks;
      newApplication.accounts = null;
      newApplication.clusters = null;
      newApplication.loadBalancers = null;
      newApplication.tasks = null;
    }

    function getApplication(applicationName) {
      return getApplicationEndpoint(applicationName).get().then(function(application) {
        var clusterLoader = clusterService.loadClusters(application);
        var loadBalancerLoader = loadBalancerService.loadLoadBalancers(application);
        var taskLoader = pond.one('applications', applicationName)
          .all('tasks')
          .getList();

        return $q.all({clusters: clusterLoader, loadBalancers: loadBalancerLoader, tasks: taskLoader})
          .then(function(results) {
            application.clusters = results.clusters;
            application.serverGroups = _.flatten(_.pluck(results.clusters, 'serverGroups'));
            application.loadBalancers = results.loadBalancers;
            application.tasks = results.tasks;
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
