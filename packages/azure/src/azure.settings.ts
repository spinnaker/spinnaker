import { IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface IAzureProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
    region?: string;
  };
}

export const AzureProviderSettings: IAzureProviderSettings = (SETTINGS.providers.azure as IAzureProviderSettings) || {
  defaults: {},
};
if (AzureProviderSettings) {
  AzureProviderSettings.resetToOriginal = SETTINGS.resetProvider('azure');
}
