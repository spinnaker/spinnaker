import { IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface ICloudFoundryProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
    region?: string;
  };
}

export const CloudFoundryProviderSettings: ICloudFoundryProviderSettings = <ICloudFoundryProviderSettings>SETTINGS.providers.cf || { defaults: {} };
if (CloudFoundryProviderSettings) {
  CloudFoundryProviderSettings.resetToOriginal = SETTINGS.resetToOriginal;
}
