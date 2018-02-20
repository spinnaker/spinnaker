import { SETTINGS } from '@spinnaker/core';

export interface ICanarySettings {
  showAllConfigs: boolean;
  reduxLogger: boolean;
  metricsAccountName: string;
  storageAccountName: string;
  defaultJudge: string;
  metricStore: string;
  defaultServiceSettings: {[key: string]: any};
  stagesEnabled: boolean;
  graphImplementation: string;
  featureDisabled: boolean;
  optInAll: boolean;
  atlasWebComponentsUrl: string;
  templatesEnabled: boolean;
}

export const CanarySettings: ICanarySettings = SETTINGS.canary || { featureDisabled: true };
