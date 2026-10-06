import { CloudProviderRegistry, SETTINGS } from '@spinnaker/core';

import { TencentcloudImageReader } from './image';

describe('Tencentcloud package entrypoint', () => {
  let tencentcloudPackage: any;

  beforeAll(async () => {
    SETTINGS.providers.tencentcloud = {};
    tencentcloudPackage = await import('./index');
  });

  it('loads successfully', () => {
    expect(tencentcloudPackage).toBeDefined();
  });

  it('registers the provider configuration', () => {
    expect(CloudProviderRegistry.getValue('tencentcloud', 'image.reader')).toBe(TencentcloudImageReader);
  });
});
