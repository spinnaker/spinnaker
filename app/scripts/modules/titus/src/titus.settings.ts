import { IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface ITitusProviderSettings extends IProviderSettings {
  autoScalingEnabled?: boolean,
  defaults: {
    account?: string;
    region?: string;
    iamProfile?: string;
  };
}

export const TitusProviderSettings: ITitusProviderSettings = <ITitusProviderSettings>SETTINGS.providers.titus || { defaults: {} };
if (TitusProviderSettings) {
  TitusProviderSettings.resetToOriginal = SETTINGS.resetToOriginal;
}
