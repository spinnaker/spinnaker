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
});
