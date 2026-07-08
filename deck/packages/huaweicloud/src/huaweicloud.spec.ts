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

  it('does not expose an Angular module token', () => {
    const huaweicloudPackage = require('./index');

    expect((huaweicloudPackage as any).HUAWEICLOUD_MODULE).toBeUndefined();
  });
});
