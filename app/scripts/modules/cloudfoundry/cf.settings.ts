import { IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface ICloudFoundryProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
    region?: string;
  };
}

export const CloudFoundryProviderSettings: ICloudFoundryProviderSettings = (SETTINGS.providers
  .cf as ICloudFoundryProviderSettings) || { defaults: {} };
if (CloudFoundryProviderSettings) {
  CloudFoundryProviderSettings.resetToOriginal = SETTINGS.resetProvider('cf');
}
