export class AcaTaskStageConfigDetails {
  watchers: string = '';
  lifetimeHours = '1';
  minimumCanaryResultScore = '10';
  combinedCanaryResultStrategy = 'LOWEST';
  canaryResultScore = '80';

  notificationHours = '1';
  useLookback = false;
  lookbackMins = 0;
  configName: string = '';
  beginCanaryAnalysisAfterMins = '0';
  canaryAnalysisIntervalMins = '15';

  accountName: string = '';
  region: string = '';
  baseline: string = '';
  canary: string = '';
  type: string = '';
}

