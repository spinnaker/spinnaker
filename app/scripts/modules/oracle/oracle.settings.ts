import { IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface IOracleProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
    region?: string;
  };
}

export const OracleProviderSettings: IOracleProviderSettings = (SETTINGS.providers
  .oracle as IOracleProviderSettings) || { defaults: {} };
if (OracleProviderSettings) {
  OracleProviderSettings.resetToOriginal = SETTINGS.resetProvider('oracle');
}
