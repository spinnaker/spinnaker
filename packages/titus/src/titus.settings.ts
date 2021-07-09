import { IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface ITitusProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
    region?: string;
    iamProfile?: string;
  };
  bakeWarning?: string;
}

export const TitusProviderSettings: ITitusProviderSettings = (SETTINGS.providers.titus as ITitusProviderSettings) || {
  defaults: {},
};
if (TitusProviderSettings) {
  TitusProviderSettings.resetToOriginal = SETTINGS.resetToOriginal;
}
