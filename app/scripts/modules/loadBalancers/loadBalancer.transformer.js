'use strict';


angular.module('deckApp.loadBalancer.transformer.service', [
  'deckApp.settings',
  'deckApp.utils.lodash',
  'deckApp.delegation',
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
