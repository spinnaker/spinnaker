import { IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface IHuaweiCloudProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
    region?: string;
  };
}

export const HuaweiCloudProviderSettings: IHuaweiCloudProviderSettings = (SETTINGS.providers
  .huaweicloud as IHuaweiCloudProviderSettings) || { defaults: {} };

if (HuaweiCloudProviderSettings) {
  HuaweiCloudProviderSettings.resetToOriginal = SETTINGS.resetProvider('huaweicloud');
}
