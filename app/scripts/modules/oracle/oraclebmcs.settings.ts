import { IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface IOracleBMCSProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
    region?: string;
  };
}

export const OracleBMCSProviderSettings: IOracleBMCSProviderSettings = (SETTINGS.providers
  .oraclebmcs as IOracleBMCSProviderSettings) || { defaults: {} };
if (OracleBMCSProviderSettings) {
  OracleBMCSProviderSettings.resetToOriginal = SETTINGS.resetProvider('oraclebmcs');
}
