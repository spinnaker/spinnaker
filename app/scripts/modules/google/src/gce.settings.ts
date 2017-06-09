import { IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface IGCEProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
    region?: string;
    zone?: string;
  };
}

export const GCEProviderSettings: IGCEProviderSettings = <IGCEProviderSettings>SETTINGS.providers.gce || { defaults: {} };
if (GCEProviderSettings) {
  GCEProviderSettings.resetToOriginal = SETTINGS.resetToOriginal;
}
