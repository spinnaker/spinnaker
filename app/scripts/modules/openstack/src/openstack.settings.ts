import { IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface IOpenStackProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
    region?: string;
  };
}

export const OpenStackProviderSettings: IOpenStackProviderSettings = (SETTINGS.providers
  .openstack as IOpenStackProviderSettings) || { defaults: {} };
if (OpenStackProviderSettings) {
  OpenStackProviderSettings.resetToOriginal = SETTINGS.resetProvider('openstack');
}
