import { IProviderSettings, SETTINGS } from 'core/config/settings';

export interface ITitusProviderSettings extends IProviderSettings {
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
