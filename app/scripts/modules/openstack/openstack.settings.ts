import { IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface IOpenStackProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
    region?: string;
  };
}

export const OpenStackProviderSettings: IOpenStackProviderSettings = <IOpenStackProviderSettings>SETTINGS.providers.openstack || { defaults: {} };
if (OpenStackProviderSettings) {
  OpenStackProviderSettings.resetToOriginal = SETTINGS.resetToOriginal;
}
