import { CloudProviderRegistry, SETTINGS } from '@spinnaker/core';

import { TencentcloudImageReader } from './image';

describe('Tencentcloud package entrypoint', () => {
  let tencentcloudPackage: any;

  beforeAll(() => {
    SETTINGS.providers.tencentcloud = {};
    tencentcloudPackage = require('./index');
  });

  it('loads successfully', () => {
    expect(tencentcloudPackage).toBeDefined();
  });

  it('registers the provider configuration', () => {
    expect(CloudProviderRegistry.getValue('tencentcloud', 'image.reader')).toBe(TencentcloudImageReader);
  });

  it('does not expose Angular module tokens', () => {
    expect((tencentcloudPackage as any).TENCENTCLOUD_MODULE).toBeUndefined();
    expect((tencentcloudPackage as any).TENCENTCLOUD_REACT_MODULE).toBeUndefined();
  });
});
