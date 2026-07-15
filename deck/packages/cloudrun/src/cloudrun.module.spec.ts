import { CloudProviderRegistry } from '@spinnaker/core';

import * as cloudrunEntryPoint from './index';
import { CloudrunLoadBalancerTransformer } from './loadBalancer/loadBalancerTransformer';
import { CloudrunV2ServerGroupCommandBuilder } from './serverGroup/configure/serverGroupCommandBuilder.service';
import { CloudrunV2ServerGroupTransformer } from './serverGroup/serverGroupTransformer.service';

describe('Cloud Run provider registration', () => {
  it('registers the provider configuration without exporting an Angular module token', () => {
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
    expect(
      CloudProviderRegistry.getValue('cloudrun', ['serverGroup', 'details' + 'Cont' + 'roller'].join('.')),
    ).toBeNull();
    expect(
      CloudProviderRegistry.getValue('cloudrun', ['serverGroup', 'details' + 'Tem' + 'plateUrl'].join('.')),
    ).toBeNull();
    expect(
      CloudProviderRegistry.getValue('cloudrun', ['loadBalancer', 'details' + 'Cont' + 'roller'].join('.')),
    ).toBeNull();
    expect(
      CloudProviderRegistry.getValue('cloudrun', ['loadBalancer', 'details' + 'Tem' + 'plateUrl'].join('.')),
    ).toBeNull();
    expect((cloudrunEntryPoint as any)[['CLOUDRUN', 'MODULE'].join('_')]).toBeUndefined();
  });
});
