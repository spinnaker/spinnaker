import { ICanaryMetricSetQueryConfig } from 'kayenta/domain';

export interface IStackdriverCanaryMetricSetQueryConfig extends ICanaryMetricSetQueryConfig {
  metricType: string;
  resourceType: string;
  crossSeriesReducer: string;
  perSeriesAligner: string;
  groupByFields: string[];
}
