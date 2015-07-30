'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.loadBalancer.transformer.service', [
  require('../caches/deckCacheFactory.js'),
  require('utils/lodash.js'),
  require('../delegation/serviceDelegate.service.js'),
])
  .factory('loadBalancerTransformer', function ( settings, _, serviceDelegate) {

    function normalizeLoadBalancerWithServerGroups(loadBalancer) {
      serviceDelegate.getDelegate(loadBalancer.provider || loadBalancer.type, 'LoadBalancerTransformer').
        normalizeLoadBalancerWithServerGroups(loadBalancer);
    }

    return {
      normalizeLoadBalancerWithServerGroups: normalizeLoadBalancerWithServerGroups,
    };

  })
  .name;
