import { SETTINGS } from '@spinnaker/core';

export interface ICanarySettings {
  showAllConfigs: boolean;
  reduxLogger: boolean;
  metricsAccountName: string;
  storageAccountName: string;
  defaultJudge: string;
  metricStore: string;
  stagesEnabled: boolean;
  stageName: string;
  stageDescription: string;
  featureDisabled: boolean;
  optInAll: boolean;
  atlasWebComponentsUrl: string;
  atlasWebComponentsPolyfillUrl: string;
  atlasGraphBaseUrl: string;
  templatesEnabled: boolean;
}

export const CanarySettings: ICanarySettings = SETTINGS.canary || { featureDisabled: true };
