'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.loadBalancer.read.service', [
    require('../naming/naming.service.js'),
    require('../cache/infrastructureCaches.js'),
    require('./loadBalancer.transformer.js'),
  ])
  .factory('loadBalancerReader', function ($q, Restangular, namingService,
                                           loadBalancerTransformer, infrastructureCaches) {

    function loadLoadBalancers(applicationName) {
      var loadBalancers = Restangular.one('applications', applicationName).all('loadBalancers').getList();
        return loadBalancers.then(function(results) {
          results.forEach(addStackToLoadBalancer);
          return $q.all(results.map(loadBalancerTransformer.normalizeLoadBalancer));
        });
    }

    function addStackToLoadBalancer(loadBalancer) {
      var nameParts = namingService.parseLoadBalancerName(loadBalancer.name);
      loadBalancer.stack = nameParts.stack;
    }

    function getLoadBalancerDetails(provider, account, region, name) {
      return Restangular.one('loadBalancers').one(account).one(region).one(name).get({'provider': provider});
    }

    function listLoadBalancers(provider) {
      return Restangular
        .all('loadBalancers')
        .withHttpConfig({cache: infrastructureCaches.loadBalancers})
        .getList({provider: provider});
    }

    return {
      loadLoadBalancers: loadLoadBalancers,
      getLoadBalancerDetails: getLoadBalancerDetails,
      listLoadBalancers: listLoadBalancers,
    };

  });
