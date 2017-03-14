export class AcaTaskStageConfigDetails {
  public watchers = '';
  public lifetimeHours = '1';
  public minimumCanaryResultScore = '10';
  public combinedCanaryResultStrategy = 'LOWEST';
  public canaryResultScore = '80';

  public notificationHours = '1';
  public useLookback = false;
  public lookbackMins = 0;
  public configName = '';
  public beginCanaryAnalysisAfterMins = '0';
  public canaryAnalysisIntervalMins = '15';

  public accountName = '';
  public region = '';
  public baseline = '';
  public canary = '';
  public type = '';
}

