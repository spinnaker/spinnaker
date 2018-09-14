import { IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface ICloudFoundryProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
  };
}

export const CloudFoundryProviderSettings: ICloudFoundryProviderSettings = (SETTINGS.providers
  .cloudfoundry as ICloudFoundryProviderSettings) || { defaults: {} };
if (CloudFoundryProviderSettings) {
  CloudFoundryProviderSettings.resetToOriginal = SETTINGS.resetProvider('cloudfoundry');
}
