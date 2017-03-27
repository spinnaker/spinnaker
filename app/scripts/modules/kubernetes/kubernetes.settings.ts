import { IProviderSettings, SETTINGS } from 'core/config/settings';

export interface IKubernetesProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
    namespace?: string;
    proxy?: string;
  };
}

export const KubernetesProviderSettings: IKubernetesProviderSettings = <IKubernetesProviderSettings>SETTINGS.providers.kubernetes || { defaults: {} };
if (KubernetesProviderSettings) {
  KubernetesProviderSettings.resetToOriginal = SETTINGS.resetToOriginal;
}
