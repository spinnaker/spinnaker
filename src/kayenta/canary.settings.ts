import { SETTINGS } from '@spinnaker/core';

export interface ICanarySettings {
  liveCalls: boolean;
  metricsAccountName: string;
  storageAccountName: string;
}

export const CanarySettings = <ICanarySettings>SETTINGS.canary;
