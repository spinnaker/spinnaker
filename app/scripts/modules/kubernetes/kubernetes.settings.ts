import { IProviderSettings, SETTINGS } from '@spinnaker/core';

export interface IKubernetesProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
    namespace?: string;
    proxy?: string;
    internalDNSNameTemplate?: string;
    instanceLinkTemplate?: string;
  };
}

export const KubernetesProviderSettings: IKubernetesProviderSettings = <IKubernetesProviderSettings>SETTINGS.providers.kubernetes || { defaults: {} };
if (KubernetesProviderSettings) {
  KubernetesProviderSettings.resetToOriginal = SETTINGS.resetToOriginal;
}
