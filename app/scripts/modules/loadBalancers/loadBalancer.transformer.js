'use strict';


angular.module('deckApp.loadBalancer.transformer.service', [
  'deckApp.settings',
  'deckApp.utils.lodash',
  'deckApp.delegation',
])
  .factory('loadBalancerTransformer', function ( settings, _, serviceDelegate) {

    function normalizeLoadBalancersWithServerGroups(application) {
      application.loadBalancers.forEach(function(loadBalancer) {
        serviceDelegate.getDelegate(loadBalancer.provider, 'LoadBalancerTransformer').
          normalizeLoadBalancerWithServerGroups(loadBalancer, application);
      });
    }

    return {
      normalizeLoadBalancersWithServerGroups: normalizeLoadBalancersWithServerGroups,
    };

  });
