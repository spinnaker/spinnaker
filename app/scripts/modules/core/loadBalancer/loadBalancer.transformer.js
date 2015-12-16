'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.loadBalancer.transformer', [
  require('../cloudProvider/serviceDelegate.service.js'),
])
  .factory('loadBalancerTransformer', function ( settings, _, serviceDelegate) {

    function normalizeLoadBalancer(loadBalancer) {
      return serviceDelegate.getDelegate(loadBalancer.provider || loadBalancer.type, 'loadBalancer.transformer').
        normalizeLoadBalancer(loadBalancer);
    }

    return {
      normalizeLoadBalancer: normalizeLoadBalancer,
    };

  });
