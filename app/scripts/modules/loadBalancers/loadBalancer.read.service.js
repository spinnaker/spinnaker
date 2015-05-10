'use strict';


angular
  .module('deckApp.loadBalancer.read.service', [
    'deckApp.caches.infrastructure',
    'deckApp.aws.loadBalancer.transformer.service',
  ])
  .factory('loadBalancerReader', function ($q, Restangular, searchService, awsLoadBalancerTransformer, infrastructureCaches) {

    function loadLoadBalancers(applicationName) {
      return Restangular
        .one('applications', applicationName)
        .all('loadBalancers').getList().then(function(loadBalancers) {
          loadBalancers.forEach(awsLoadBalancerTransformer.normalizeLoadBalancerWithServerGroups);
          return loadBalancers;
        });
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
      loadLoadBalancers: loadLoadBalancers,
      getLoadBalancerDetails: getLoadBalancerDetails,
      listAWSLoadBalancers: listAWSLoadBalancers,
      listGCELoadBalancers: listGCELoadBalancers
    };

  });
