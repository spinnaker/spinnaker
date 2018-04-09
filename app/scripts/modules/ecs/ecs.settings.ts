import { IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface IECSProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
    region?: string;
    zone?: string;
  };
}

export const IECSProviderSettings: IECSProviderSettings = <IECSProviderSettings>SETTINGS.providers.ecs || {
  defaults: {},
};
if (IECSProviderSettings) {
  IECSProviderSettings.resetToOriginal = SETTINGS.resetProvider('ecs');
}
