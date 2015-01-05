'use strict';


angular
  .module('deckApp.loadBalancer.read.service', ['deckApp.caches.infrastructure'])
  .factory('loadBalancerReader', function ($q, Restangular, searchService, infrastructureCaches) {

    function loadLoadBalancersByApplicationName(applicationName) {
      return searchService.search('gate', {q: applicationName, type: 'loadBalancers', pageSize: 10000}).then(function(searchResults) {
        return _.filter(searchResults.results, { application: applicationName });
      });
    }

    function getLoadBalancer(loadBalancer) {
      var promise = Restangular.one('loadBalancers', loadBalancer.loadBalancer).get({provider: loadBalancer.provider});
      return promise.then(function(loadBalancerRollup) {
        if (angular.isUndefined(loadBalancerRollup.accounts)) { return []; }
        var loadBalancers = [];
        loadBalancerRollup.accounts.forEach(function (account) {
          account.regions.forEach(function (region) {
            region.loadBalancers.forEach(function (loadBalancer) {
              loadBalancer.account = account.name;
              loadBalancers.push(loadBalancer);
            });
          });
        });
        return loadBalancers;
      });
    }


    function loadLoadBalancers(application, loadBalancersByApplicationName) {
      var loadBalancerResults = loadBalancersByApplicationName;

      loadBalancerResults = _.map(loadBalancerResults, function(individualResult) {
        return _.pick(individualResult, 'loadBalancer', 'provider');
      });

      application.accounts.forEach(function(account) {
        var accountClusters = application.clusters[account] || [];

        accountClusters.forEach(function(cluster) {
          cluster.loadBalancers.forEach(function(loadBalancerName) {
            loadBalancerResults.push({loadBalancer: loadBalancerName, provider: cluster.provider});
          });
        });
      });

      loadBalancerResults = _.unique(_.flatten(loadBalancerResults), 'loadBalancer');

      var loadBalancerPromises = [];

      loadBalancerResults.forEach(function(loadBalancer) {
        var loadBalancerPromise = getLoadBalancer(loadBalancer);

        loadBalancerPromises.push(loadBalancerPromise);
      });

      return $q.all(loadBalancerPromises).then(_.flatten);
    }


    function getLoadBalancerDetails(provider, account, region, name) {
      return Restangular.one('loadBalancers').one(account).one(region).one(name).get({'provider': provider});
    }

    function listAWSLoadBalancers() {
      return Restangular
        .all('loadBalancers')
        .withHttpConfig({cache: infrastructureCaches.loadBalancers})
        .getList({provider: 'aws'});
    }

    function listGCELoadBalancers() {
      return Restangular
        .all('loadBalancers')
        .getList({provider: 'gce'});
    }

    return {
      loadLoadBalancersByApplicationName: loadLoadBalancersByApplicationName,
      loadLoadBalancers: loadLoadBalancers,
      getLoadBalancerDetails: getLoadBalancerDetails,
      listAWSLoadBalancers: listAWSLoadBalancers,
      listGCELoadBalancers: listGCELoadBalancers
    };

  });
