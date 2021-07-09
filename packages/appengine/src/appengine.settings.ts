import { IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface IAppengineProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
  };
}

export const AppengineProviderSettings: IAppengineProviderSettings = (SETTINGS.providers
  .appengine as IAppengineProviderSettings) || { defaults: {} };
if (AppengineProviderSettings) {
  AppengineProviderSettings.resetToOriginal = SETTINGS.resetProvider('appengine');
}
