import { IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface IECSProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
    region?: string;
    zone?: string;
  };
}

export const IECSProviderSettings: IECSProviderSettings = (SETTINGS.providers.ecs as IECSProviderSettings) || {
  defaults: {},
};
if (IECSProviderSettings) {
  IECSProviderSettings.resetToOriginal = SETTINGS.resetProvider('ecs');
}
