export class AcaTaskStageConfigDetails {
  watchers: string = '';
  lifetimeHours: string = '1';
  minimumCanaryResultScore: string = '10';
  combinedCanaryResultStrategy: string = 'LOWEST';
  canaryResultScore: string = '80';

  notificationHours: string = '1';
  useLookback = false;
  lookbackMins =  0;
  configName: string = '';
  beginCanaryAnalysisAfterMins: string = '0';
  canaryAnalysisIntervalMins: string = '15';

  accountName: string = '';
  region: string = '';
  baseline: string = '';
  canary: string = '';
  type: string = '';
}

