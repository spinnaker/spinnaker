import { IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface IDcosProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
    dcosCluster?: string;
  };
}

export const DcosProviderSettings: IDcosProviderSettings = <IDcosProviderSettings>SETTINGS.providers.dcos || { defaults: {} };
if (DcosProviderSettings) {
  DcosProviderSettings.resetToOriginal = SETTINGS.resetProvider('dcos');
}
