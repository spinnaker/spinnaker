import { CloudProviderRegistry, SETTINGS } from '@spinnaker/core';

describe('HuaweiCloud package entrypoint', () => {
  beforeAll(() => {
    SETTINGS.providers.huaweicloud = { defaults: {} };
  });

  it('loads successfully', () => {
    expect(() => require('./index')).not.toThrow();
  });

  it('registers the provider configuration', () => {
    require('./index');

    expect(CloudProviderRegistry.getProvider('huaweicloud')).toEqual({ name: 'huaweicloud' });
  });
});
