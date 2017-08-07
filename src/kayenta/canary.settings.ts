import { SETTINGS } from '@spinnaker/core';

export interface ICanarySettings {
  liveCalls: boolean;
  metricsAccountName: string;
  storageAccountName: string;
  judge: string;
}

export const CanarySettings = <ICanarySettings>SETTINGS.canary;
