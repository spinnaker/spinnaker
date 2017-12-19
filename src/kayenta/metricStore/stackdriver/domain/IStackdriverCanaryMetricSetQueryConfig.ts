import { ICanaryMetricSetQueryConfig } from 'kayenta/domain';

export interface IStackdriverCanaryMetricSetQueryConfig extends ICanaryMetricSetQueryConfig {
  metricType: string;
  groupByFields: string[];

  // These two could be moved to the base interface if other
  // metric store query configs use them.
  customFilter?: string;
  customFilterTemplate?: string;
}
