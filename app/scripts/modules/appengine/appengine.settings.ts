import { IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface IAppengineProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
    editLoadBalancerStageEnabled?: boolean;
  };
}

export const AppengineProviderSettings: IAppengineProviderSettings = <IAppengineProviderSettings>SETTINGS.providers.appengine || { defaults: {} };
if (AppengineProviderSettings) {
  AppengineProviderSettings.resetToOriginal = SETTINGS.resetToOriginal;
}
