'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.loadBalancer.read.service', [
    require('../naming/naming.service.js'),
    require('../cache/infrastructureCaches.js'),
    require('./loadBalancer.transformer.js'),
    require('../api/api.service')
  ])
  .factory('loadBalancerReader', function ($q, API, namingService,
                                           loadBalancerTransformer, infrastructureCaches) {

    function loadLoadBalancers(applicationName) {
      var loadBalancers = API.one('applications').one(applicationName).all('loadBalancers').getList();
        return loadBalancers.then(function(results) {
          results.forEach(addStackToLoadBalancer);
          return $q.all(results.map(loadBalancerTransformer.normalizeLoadBalancer));
        });
    }

    function addStackToLoadBalancer(loadBalancer) {
      var nameParts = namingService.parseLoadBalancerName(loadBalancer.name);
      loadBalancer.stack = nameParts.stack;
      loadBalancer.detail = nameParts.freeFormDetails;
    }

    function getLoadBalancerDetails(provider, account, region, name) {
      return API.one('loadBalancers').one(account).one(region).one(name).withParams({'provider': provider}).get();
    }

    function listLoadBalancers(provider) {
      return API
        .one('loadBalancers')
        .useCache(infrastructureCaches.loadBalancers)
        .withParams({provider: provider})
        .get();
    }

    return {
      loadLoadBalancers: loadLoadBalancers,
      getLoadBalancerDetails: getLoadBalancerDetails,
      listLoadBalancers: listLoadBalancers,
    };

  });
