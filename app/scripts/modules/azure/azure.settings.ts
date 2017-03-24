import { IProviderSettings, SETTINGS } from 'core/config/settings';

export interface IAzureProviderSettings extends IProviderSettings {
  defaults: {
    account: string;
    region: string;
  };
}

export const AzureProviderSettings: IAzureProviderSettings = <IAzureProviderSettings>SETTINGS.providers.azure;
if (AzureProviderSettings) {
  AzureProviderSettings.resetToOriginal = SETTINGS.resetToOriginal;
}
