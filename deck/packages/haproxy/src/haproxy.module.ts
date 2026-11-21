import { CloudProviderRegistry } from '@spinnaker/core';

import { HaProxyLoadBalancerTransformer } from './HaProxyLoadBalancerTransformer';
import { HaProxyLoadBalancerDetails } from './loadBalancer/details/HaProxyLoadBalancerDetails';

CloudProviderRegistry.registerProvider('haproxy', {
  name: 'HAProxy',
  loadBalancer: {
    transformer: HaProxyLoadBalancerTransformer,
    details: HaProxyLoadBalancerDetails,
  },
});
