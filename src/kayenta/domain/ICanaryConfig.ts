export interface ICanaryConfig {
  createdTimestamp: number;
  updatedTimestamp: number;
  createdTimestampIso: string;
  updatedTimestampIso: string;
  name: string;
  description: string;
  configVersion: string;
  metrics: ICanaryMetricConfig[];
  services: {[key: string]: ICanaryServiceConfig};
  classifier: ICanaryClassifierConfig;
}

export interface ICanaryMetricConfig {
  name: string;
  serviceName: string;
  query: ICanaryMetricSetQueryConfig;
  groups: string[];
  analysisConfigs: {[key: string]: any};
}

export interface ICanaryMetricSetQueryConfig {
  type: string;
}

export interface ICanaryServiceConfig {
  name: string;
  type: string;
  region: string;
  environment: string;
}

export interface ICanaryClassifierConfig {
  groupWeights: {[key: string]: number};
  scoreThreshold: ICanaryClassifierThresholdsConfig;
}

export interface ICanaryClassifierThresholdsConfig {
  pass: number;
  marginal: number;
}
