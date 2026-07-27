import { CloudProviderRegistry } from '@spinnaker/core';

import './index';
import { CloudrunLoadBalancerTransformer } from './loadBalancer/loadBalancerTransformer';
import { CloudrunV2ServerGroupCommandBuilder } from './serverGroup/configure/serverGroupCommandBuilder.service';
import { CloudrunV2ServerGroupTransformer } from './serverGroup/serverGroupTransformer.service';

describe('Cloud Run provider registration', () => {
  it('registers the provider configuration', () => {
    expect(CloudProviderRegistry.getValue('cloudrun', 'instance.details')).toBeDefined();
    expect(CloudProviderRegistry.getValue('cloudrun', 'serverGroup.commandBuilder')).toBe(
      CloudrunV2ServerGroupCommandBuilder,
    );
    expect(CloudProviderRegistry.getValue('cloudrun', 'serverGroup.transformer')).toBe(
      CloudrunV2ServerGroupTransformer,
    );
    expect(CloudProviderRegistry.getValue('cloudrun', 'loadBalancer.transformer')).toBe(
      CloudrunLoadBalancerTransformer,
    );
    expect(CloudProviderRegistry.getValue('cloudrun', 'loadBalancer.CreateLoadBalancerModal')).toBeDefined();
  });
});
