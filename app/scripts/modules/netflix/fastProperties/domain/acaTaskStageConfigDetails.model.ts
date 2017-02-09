export class AcaTaskStageConfigDetails {
  watchers = '';
  lifetimeHours = '1';
  minimumCanaryResultScore = '10';
  combinedCanaryResultStrategy = 'LOWEST';
  canaryResultScore = '80';

  notificationHours = '1';
  useLookback = false;
  lookbackMins = 0;
  configName = '';
  beginCanaryAnalysisAfterMins = '0';
  canaryAnalysisIntervalMins = '15';

  accountName = '';
  region = '';
  baseline = '';
  canary = '';
  type = '';
}

