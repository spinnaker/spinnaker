import { IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface IAzureProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
    region?: string;
  };
}

export const AzureProviderSettings: IAzureProviderSettings = <IAzureProviderSettings>SETTINGS.providers.azure || { defaults: {} };
if (AzureProviderSettings) {
  AzureProviderSettings.resetToOriginal = SETTINGS.resetToOriginal;
}
