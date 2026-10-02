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
  template?: string;
  // Legacy field from the old named/saved-template mechanism. The backend normalizes this into
  // `template` on read (via QueryConfigUtils), so nothing in the frontend sets or reads it
  // anymore -- it's kept here only because the raw JSON for a config the backend hasn't
  // rewritten in place yet may still carry it.
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
  measure?: string;
}

export interface ICanaryJudgeConfig {
  name: string;
  judgeConfigurations: { [key: string]: any };
}
