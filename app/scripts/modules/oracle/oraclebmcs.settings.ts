import { IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface IOracleBMCSProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
    region?: string;
  };
}

export const OracleBMCSProviderSettings: IOracleBMCSProviderSettings = <IOracleBMCSProviderSettings>SETTINGS.providers.oraclebmcs || { defaults: {} };
if (OracleBMCSProviderSettings) {
  OracleBMCSProviderSettings.resetToOriginal = SETTINGS.resetToOriginal;
}
