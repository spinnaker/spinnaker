'use strict';


angular.module('spinnaker.loadBalancer.transformer.service', [
  'spinnaker.settings',
  'spinnaker.utils.lodash',
  'spinnaker.delegation',
])
  .factory('loadBalancerTransformer', function ( settings, _, serviceDelegate) {

    function normalizeLoadBalancerWithServerGroups(loadBalancer) {
      serviceDelegate.getDelegate(loadBalancer.provider || loadBalancer.type, 'LoadBalancerTransformer').
        normalizeLoadBalancerWithServerGroups(loadBalancer);
    }

    return {
      normalizeLoadBalancerWithServerGroups: normalizeLoadBalancerWithServerGroups,
    };

  });
