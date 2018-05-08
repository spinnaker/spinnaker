import { IInstanceStorage, IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface IGCEProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
    region?: string;
    zone?: string;
    instanceTypeStorage?: IInstanceStorage;
  };
}

export const GCEProviderSettings: IGCEProviderSettings = (SETTINGS.providers.gce as IGCEProviderSettings) || {
  defaults: {},
};
if (GCEProviderSettings) {
  GCEProviderSettings.resetToOriginal = SETTINGS.resetProvider('gce');
}
