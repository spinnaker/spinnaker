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
  templates: { [key: string]: string };
  classifier: ICanaryClassifierConfig;
  judge: ICanaryJudgeConfig;
}

export interface ICanaryMetricConfig<T extends ICanaryMetricSetQueryConfig = any> {
  id: string;
  name: string;
  query: T;
  groups: string[];
  analysisConfigurations: {
    [key: string]: any;
    effectSize?: ICanaryMetricEffectSizeConfig;
  };
  scopeName: string;
  isNew?: boolean;
}

export interface ICanaryMetricSetQueryConfig {
  type: string;
  serviceType: string;
  customInlineTemplate?: string;
  customFilterTemplate?: string;
}

export interface IGroupWeights {
  [group: string]: number;
}

export interface ICanaryClassifierConfig {
  groupWeights: IGroupWeights;
}

export interface ICanaryMetricEffectSizeConfig {
  allowedIncrease?: number;
  allowedDecrease?: number;
  criticalIncrease?: number;
  criticalDecrease?: number;
}

export interface ICanaryJudgeConfig {
  name: string;
  judgeConfigurations: { [key: string]: any };
}
