'use strict';

import {API_SERVICE} from 'core/api/api.service';
import {NAMING_SERVICE} from 'core/naming/naming.service';

let angular = require('angular');
import {INFRASTRUCTURE_CACHE_SERVICE} from 'core/cache/infrastructureCaches.service';

module.exports = angular
  .module('spinnaker.core.loadBalancer.read.service', [
    NAMING_SERVICE,
    INFRASTRUCTURE_CACHE_SERVICE,
    require('./loadBalancer.transformer.js'),
    API_SERVICE
  ])
  .factory('loadBalancerReader', function ($q, API, namingService,
                                           loadBalancerTransformer, infrastructureCaches) {

    function loadLoadBalancers(applicationName) {
      var loadBalancers = API.one('applications').one(applicationName).all('loadBalancers').getList();
        return loadBalancers.then(function(results) {
          results = loadBalancerTransformer.normalizeLoadBalancerSet(results);
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
        .useCache(infrastructureCaches.get('loadBalancers'))
        .withParams({provider: provider})
        .get();
    }

    return {
      loadLoadBalancers: loadLoadBalancers,
      getLoadBalancerDetails: getLoadBalancerDetails,
      listLoadBalancers: listLoadBalancers,
    };

  });
