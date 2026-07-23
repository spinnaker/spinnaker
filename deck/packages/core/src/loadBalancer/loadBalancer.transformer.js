'use strict';

import { module } from 'angular';
import { chain } from 'lodash';

import { PROVIDER_SERVICE_DELEGATE } from '../cloudProvider/providerService.delegate';

export const CORE_LOADBALANCER_LOADBALANCER_TRANSFORMER = 'spinnaker.core.loadBalancer.transformer';
export const name = CORE_LOADBALANCER_LOADBALANCER_TRANSFORMER; // for backwards compatibility

export function createLoadBalancerTransformer(providerServiceDelegate) {
  function normalizeLoadBalancer(loadBalancer) {
    const provider = loadBalancer.provider || loadBalancer.type;
    if (!providerServiceDelegate.hasDelegate(provider, 'loadBalancer.transformer')) {
      return Promise.resolve(loadBalancer);
    }

    return providerServiceDelegate
      .getDelegate(provider, 'loadBalancer.transformer')
      .normalizeLoadBalancer(loadBalancer);
  }

  function normalizeLoadBalancerSet(loadBalancers) {
    const setTransformers = chain(loadBalancers)
      .map((loadBalancer) => loadBalancer.provider || loadBalancer.type)
      .uniq()
      .filter((provider) => providerServiceDelegate.hasDelegate(provider, 'loadBalancer.setTransformer'))
      .map((provider) => providerServiceDelegate.getDelegate(provider, 'loadBalancer.setTransformer'))
      .value();

    return setTransformers.reduce(
      (currentLoadBalancers, transformer) => transformer.normalizeLoadBalancerSet(currentLoadBalancers),
      loadBalancers,
    );
  }

  return {
    normalizeLoadBalancer: normalizeLoadBalancer,
    normalizeLoadBalancerSet: normalizeLoadBalancerSet,
  };
}

module(CORE_LOADBALANCER_LOADBALANCER_TRANSFORMER, [PROVIDER_SERVICE_DELEGATE]).factory('loadBalancerTransformer', [
  'providerServiceDelegate',
  createLoadBalancerTransformer,
]);
