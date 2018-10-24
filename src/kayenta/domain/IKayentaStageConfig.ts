export interface IKayentaStage {
  canaryConfig: IKayentaStageCanaryConfig;
  analysisType: KayentaAnalysisType;
  deployments: IKayentaStageDeployments;
  isNew: boolean;
}

export interface IKayentaStageCanaryConfig {
  beginCanaryAnalysisAfterMins?: string;
  canaryAnalysisIntervalMins: string;
  canaryConfigId: string;
  scopes: IKayentaStageCanaryConfigScope[];
  combinedCanaryResultStrategy: string;
  lifetimeHours?: string;
  lifetimeDuration?: string; // String to be converted to Java.time.Duration in Orca (https://github.com/spinnaker/orca/blob/master/orca-kayenta/src/main/kotlin/com/netflix/spinnaker/orca/kayenta/model/KayentaCanaryContext.kt#L32)
  lookbackMins?: string;
  metricsAccountName: string;
  scoreThresholds: {
    pass: string;
    marginal: string;
  };
  storageAccountName: string;
}

export interface IKayentaStageCanaryConfigScope {
  scopeName: string;
  controlScope?: string;
  controlLocation?: string;
  experimentScope?: string;
  experimentLocation?: string;
  startTimeIso?: string;
  endTimeIso?: string;
  step?: number;
  extendedScopeParams: { [key: string]: string };
}

export interface IKayentaStageDeployments {
  baseline: {
    cloudProvider: string;
    application: string;
    account: string;
    cluster: string;
  };
  serverGroupPairs: IKayentaServerGroupPair[];
  delayBeforeCleanup: string; // String to be converted to Java.time.Duration in Orca (https://github.com/spinnaker/orca/blob/master/orca-kayenta/src/main/kotlin/com/netflix/spinnaker/orca/kayenta/model/Deployments.kt#L33)
}

export interface IKayentaServerGroupPair {
  control: any;
  experiment: any;
}

export enum KayentaAnalysisType {
  RealTimeAutomatic = 'realTimeAutomatic',
  RealTime = 'realTime',
  Retrospective = 'retrospective',
}
