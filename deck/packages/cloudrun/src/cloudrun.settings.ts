import type { IProviderSettings } from '@spinnaker/core';
import { SETTINGS } from '@spinnaker/core';

export interface ICloudrunProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
  };
}

export const CloudrunProviderSettings: ICloudrunProviderSettings = (SETTINGS.providers
  .cloudrun as ICloudrunProviderSettings) || { defaults: {} };
if (CloudrunProviderSettings) {
  CloudrunProviderSettings.resetToOriginal = SETTINGS.resetProvider('cloudrun');
}
