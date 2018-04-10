import { IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface IKubernetesProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
    namespace?: string;
    proxy?: string;
    internalDNSNameTemplate?: string;
    instanceLinkTemplate?: string;
    rrb?: boolean;
    apiPrefix?: string;
  };
}

export const KubernetesProviderSettings: IKubernetesProviderSettings = (SETTINGS.providers
  .kubernetes as IKubernetesProviderSettings) || { defaults: {} };
if (KubernetesProviderSettings) {
  KubernetesProviderSettings.resetToOriginal = SETTINGS.resetProvider('kubernetes');
}
