import { ICanaryMetricSetQueryConfig } from 'kayenta/domain';

export interface IStackdriverCanaryMetricSetQueryConfig extends ICanaryMetricSetQueryConfig {
  metricType: string;
  groupByFields: string[];
}
