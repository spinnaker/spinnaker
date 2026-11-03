import { CloudProviderRegistry, SETTINGS } from '@spinnaker/core';

describe('HuaweiCloud package entrypoint', () => {
  beforeAll(() => {
    SETTINGS.providers.huaweicloud = { defaults: {} };
  });

  it('loads successfully', async () => {
    await expect(import('./index')).resolves.toBeDefined();
  });

  it('registers the provider configuration', async () => {
    await import('./index');

    expect(CloudProviderRegistry.getProvider('huaweicloud')).toEqual({ name: 'huaweicloud' });
  });
});
