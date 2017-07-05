'use strict';

const angular = require('angular');

import { chain, flow } from 'lodash';

import { PROVIDER_SERVICE_DELEGATE } from 'core/cloudProvider/providerService.delegate';

module.exports = angular.module('spinnaker.core.loadBalancer.transformer', [
  PROVIDER_SERVICE_DELEGATE
])
  .factory('loadBalancerTransformer', function (providerServiceDelegate) {

    function normalizeLoadBalancer(loadBalancer) {
      return providerServiceDelegate.getDelegate(loadBalancer.provider || loadBalancer.type, 'loadBalancer.transformer').
        normalizeLoadBalancer(loadBalancer);
    }

    function normalizeLoadBalancerSet(loadBalancers) {
      let setNormalizers = chain(loadBalancers)
        .filter((lb) => providerServiceDelegate.hasDelegate(lb.provider || lb.type, 'loadBalancer.setTransformer'))
        .compact()
        .map((lb) => providerServiceDelegate
          .getDelegate(lb.provider || lb.type, 'loadBalancer.setTransformer').normalizeLoadBalancerSet)
        .uniq()
        .value();

      if (setNormalizers.length) {
        return flow(setNormalizers)(loadBalancers);
      } else {
        return loadBalancers;
      }
    }

    return {
      normalizeLoadBalancer: normalizeLoadBalancer,
      normalizeLoadBalancerSet: normalizeLoadBalancerSet,
    };

  });
