export interface ICanaryConfig {
  applications: string[];
  id?: string;
  createdTimestamp?: number;
  updatedTimestamp?: number;
  createdTimestampIso?: string;
  updatedTimestampIso?: string;
  isNew?: boolean;
  name: string;
  description: string;
  configVersion: string;
  metrics: ICanaryMetricConfig[];
  templates: {[key: string]: string};
  services: {[key: string]: ICanaryServiceConfig};
  classifier: ICanaryClassifierConfig;
  judge: ICanaryJudgeConfig;
}

export interface ICanaryMetricConfig {
  id: string;
  name: string;
  query: ICanaryMetricSetQueryConfig;
  groups: string[];
  analysisConfigurations: {
    [key: string]: any;
  };
  isNew?: boolean;
}

export interface ICanaryMetricSetQueryConfig {
  [key: string]: any;
  type: string;
}

export interface ICanaryServiceConfig {
  name: string;
  type: string;
  region: string;
  environment: string;
}

export type IGroupWeights = {[group: string]: number};

export interface ICanaryClassifierConfig {
  groupWeights: IGroupWeights;
  scoreThresholds: ICanaryClassifierThresholdsConfig;
}

export interface ICanaryClassifierThresholdsConfig {
  pass: number;
  marginal: number;
}

export interface ICanaryJudgeConfig {
  name: string;
  judgeConfigurations: {[key: string]: any};
}
