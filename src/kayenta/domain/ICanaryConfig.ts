export interface ICanaryConfig {
  createdTimestamp?: number;
  updatedTimestamp?: number;
  createdTimestampIso?: string;
  updatedTimestampIso?: string;
  isNew?: boolean;
  name: string;
  description: string;
  configVersion: string;
  metrics: ICanaryMetricConfig[];
  services: {[key: string]: ICanaryServiceConfig};
  classifier: ICanaryClassifierConfig;
}

export interface ICanaryMetricConfig {
  id: string;
  name: string;
  serviceName: string;
  query: ICanaryMetricSetQueryConfig;
  groups: string[];
  analysisConfigurations: {
    [key: string]: any;
    canary?: {
      judge?: string;
    }
  };
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

export type GroupWeights = {[group: string]: number};

export interface ICanaryClassifierConfig {
  groupWeights: GroupWeights;
  scoreThresholds: ICanaryClassifierThresholdsConfig;
}

export interface ICanaryClassifierThresholdsConfig {
  pass: number;
  marginal: number;
}
