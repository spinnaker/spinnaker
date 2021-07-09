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

export const OracleDefaultProviderSettings = {
  defaults: { account: 'DEFAULT', bakeryRegions: 'us-phoenix-1', region: 'us-phoenix-1' },
};
