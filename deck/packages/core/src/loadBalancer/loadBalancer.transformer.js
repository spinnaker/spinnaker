'use strict';

import { module } from 'angular';
import { chain, flow } from 'lodash';

import { PROVIDER_SERVICE_DELEGATE } from '../cloudProvider/providerService.delegate';

export const CORE_LOADBALANCER_LOADBALANCER_TRANSFORMER = 'spinnaker.core.loadBalancer.transformer';
export const name = CORE_LOADBALANCER_LOADBALANCER_TRANSFORMER; // for backwards compatibility
module(CORE_LOADBALANCER_LOADBALANCER_TRANSFORMER, [PROVIDER_SERVICE_DELEGATE]).factory('loadBalancerTransformer', [
  'providerServiceDelegate',
  function (providerServiceDelegate) {
    function normalizeLoadBalancer(loadBalancer) {
      return providerServiceDelegate
        .getDelegate(loadBalancer.provider || loadBalancer.type, 'loadBalancer.transformer')
        .normalizeLoadBalancer(loadBalancer);
    }

    function normalizeLoadBalancerSet(loadBalancers) {
      const setNormalizers = chain(loadBalancers)
        .filter((lb) => providerServiceDelegate.hasDelegate(lb.provider || lb.type, 'loadBalancer.setTransformer'))
        .compact()
        .map(
          (lb) =>
            providerServiceDelegate.getDelegate(lb.provider || lb.type, 'loadBalancer.setTransformer')
              .normalizeLoadBalancerSet,
        )
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
  },
]);
