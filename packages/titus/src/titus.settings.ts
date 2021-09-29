import type { IProviderSettings } from '@spinnaker/core';
import { SETTINGS } from '@spinnaker/core';

export interface ITitusProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
    region?: string;
    subnetType?: string;
    iamProfile?: string;
  };
  serverGroups?: {
    recommendedSubnets?: string[];
  };
  scalingActivities?: string[];
}

export const TitusProviderSettings: ITitusProviderSettings = (SETTINGS.providers.titus as ITitusProviderSettings) || {
  defaults: {},
};
if (TitusProviderSettings) {
  TitusProviderSettings.resetToOriginal = SETTINGS.resetToOriginal;
}
