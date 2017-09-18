import { SETTINGS } from '@spinnaker/core';

export interface ICanarySettings {
  liveCalls: boolean;
  metricsAccountName: string;
  storageAccountName: string;
  defaultJudge: string;
  metricStore: string;
  defaultServiceSettings: {[key: string]: any};
}

export const CanarySettings = <ICanarySettings>SETTINGS.canary;
