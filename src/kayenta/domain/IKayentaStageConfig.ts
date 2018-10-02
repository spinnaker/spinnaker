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
  lifetimeDuration?: string;
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
  delayBeforeCleanup: number;
}

export interface IKayentaServerGroupPair {
  control: any;
  experiment: any;
}

export interface IKayentaStageLifetime {
  hours?: number;
  minutes?: number;
}

export enum KayentaAnalysisType {
  RealTimeAutomatic = 'realTimeAutomatic',
  RealTime = 'realTime',
  Retrospective = 'retrospective',
}
