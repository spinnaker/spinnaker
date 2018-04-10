import { IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface IAppengineProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
    editLoadBalancerStageEnabled?: boolean;
    containerImageUrlDeployments?: boolean;
  };
}

export const AppengineProviderSettings: IAppengineProviderSettings = (SETTINGS.providers
  .appengine as IAppengineProviderSettings) || { defaults: {} };
if (AppengineProviderSettings) {
  AppengineProviderSettings.resetToOriginal = SETTINGS.resetProvider('appengine');
}
