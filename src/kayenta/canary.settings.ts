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
  executionsCountOptions: number[];
  defaultExecutionCount: number;
  stageDescription: string;
  featureDisabled: boolean;
  optInAll: boolean;
  atlasWebComponentsUrl: string;
  atlasWebComponentsPolyfillUrl: string;
  atlasGraphBaseUrl: string;
  templatesEnabled: boolean;
  manualAnalysisEnabled: boolean;
  disableConfigEdit: boolean;
  legacySiteLocalFieldsEnabled: boolean; // legacy fields for backwards-compat with old systems, no long term support planned
}

export const CanarySettings: ICanarySettings = SETTINGS.canary || { featureDisabled: true };
