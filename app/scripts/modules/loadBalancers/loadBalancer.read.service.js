'use strict';


angular
  .module('deckApp.loadBalancer.read.service', ['deckApp.caches.infrastructure'])
  .factory('loadBalancerReader', function ($q, Restangular, searchService, infrastructureCaches) {

    function loadLoadBalancersByApplicationName(applicationName) {
      return searchService.search('gate', {q: applicationName, type: 'loadBalancers', pageSize: 10000}).then(function(searchResults) {
        return _.filter(searchResults.results, { application: applicationName });
      });
    }

    function loadLoadBalancers(application, loadBalancersFromSearch) {

      var allLoadBalancers = [];
      application.serverGroups.forEach(function(serverGroup) {
        serverGroup.loadBalancers.forEach(function(loadBalancer) {
          allLoadBalancers.push({
            name: loadBalancer,
            vpcId: serverGroup.vpcId,
            provider: serverGroup.type,
            account: serverGroup.account,
            region: serverGroup.region
          });
        });
      });
      loadBalancersFromSearch.forEach(function(loadBalancer) {
        allLoadBalancers.push({
          name: loadBalancer.loadBalancer,
          vpcId: loadBalancer.vpcId,
          provider: loadBalancer.provider,
          account: loadBalancer.account,
          region: loadBalancer.region
        });
      });

      if (allLoadBalancers) {
        return _.uniq(allLoadBalancers, function(lb) {
          return [lb.name, lb.vpcId, lb.provider, lb.account, lb.region].join(':');
        });
      }

      return [];
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
        .withHttpConfig({cache: infrastructureCaches.loadBalancers})
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
