import { IProviderSettings, SETTINGS } from 'core/config/settings';

export interface IGCEProviderSettings extends IProviderSettings {
  defaults: {
    account: string;
    region: string;
    zone: string;
  };
}

export const GCEProviderSettings: IGCEProviderSettings = <IGCEProviderSettings>SETTINGS.providers.gce;
if (GCEProviderSettings) {
  GCEProviderSettings.resetToOriginal = SETTINGS.resetToOriginal;
}
