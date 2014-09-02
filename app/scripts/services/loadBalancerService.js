'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('loadBalancerService', function (searchService, settings, $q, Restangular, _) {

    var oortEndpoint = Restangular.withConfig(function (RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.oortUrl);
    });

    function loadLoadBalancers(application) {
      var loadBalancerNames = [],
        loadBalancerPromises = [];

      application.accounts.forEach(function(account) {
        var accountClusters = application.clusters[account];
        accountClusters.forEach(function(cluster) {
          loadBalancerNames.push(cluster.loadBalancers);
        });
      });

      loadBalancerNames = _.unique(_.flatten(loadBalancerNames));

      loadBalancerNames.forEach(function(loadBalancer) {
        var loadBalancerPromise = getLoadBalancer(loadBalancer, application);
        loadBalancerPromises.push(loadBalancerPromise);
      });

      return $q.all(loadBalancerPromises).then(function(loadBalancers) {
        application.loadBalancers = _.flatten(loadBalancers);
      });

    }

    function updateHealthCounts(application) {
      application.loadBalancers.forEach(function(loadBalancer) {
        var instances = loadBalancer.getInstances();
        loadBalancer.healthCounts = {
          upCount: instances.filter(function (instance) {
            return instance.healthStatus === 'Healthy';
          }).length,
          downCount: instances.filter(function (instance) {
            return instance.healthStatus === 'Unhealthy';
          }).length,
          unknownCount: instances.filter(function (instance) {
            return instance.healthStatus === 'Unknown';
          }).length
        };
      });
    }

    function getLoadBalancer(name, application) {
      var promise = oortEndpoint.one('aws').one('loadBalancers', name).get();
      return promise.then(function(loadBalancerRollup) {
        var loadBalancers = [];
        loadBalancerRollup.accounts.forEach(function (account) {
          account.regions.forEach(function (region) {
            region.loadBalancers.forEach(function (loadBalancer) {
              loadBalancer.getServerGroups = function() {
                return application.getServerGroups().filter(function(serverGroup) {
                  return application.serverGroupIsInLoadBalancer(serverGroup, loadBalancer);
                });
              };
              loadBalancer.getInstances = function() {
                return _.flatten(_.collect(loadBalancer.getServerGroups(), 'instances'));
              };
              loadBalancer.account = account.name;
              loadBalancers.push(loadBalancer);
            });
          });
        });
        return loadBalancers;
      });
    }

    function normalizeLoadBalancersWithServerGroups(application) {
      updateHealthCounts(application);
    }


    return {
      loadLoadBalancers: loadLoadBalancers,
      normalizeLoadBalancersWithServerGroups: normalizeLoadBalancersWithServerGroups
    };

  });
