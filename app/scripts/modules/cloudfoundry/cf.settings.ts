import { IProviderSettings, SETTINGS } from 'core/config/settings';

export interface ICloudFoundryProviderSettings extends IProviderSettings {
  defaults: {
    account: string;
    region: string;
  };
}

export const CloudFoundryProviderSettings: ICloudFoundryProviderSettings = <ICloudFoundryProviderSettings>SETTINGS.providers.cf;
if (CloudFoundryProviderSettings) {
  CloudFoundryProviderSettings.resetToOriginal = SETTINGS.resetToOriginal;
}
